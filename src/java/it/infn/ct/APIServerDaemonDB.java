/**************************************************************************
Copyright (c) 2011:
Istituto Nazionale di Fisica Nucleare (INFN), Italy
Consorzio COMETA (COMETA), Italy

See http://www.infn.it and and http://www.consorzio-cometa.it for details on
the copyright holders.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

@author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
****************************************************************************/
package it.infn.ct;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

//import java.util.logging.Logger;
import org.apache.log4j.Logger;

/**
 * Class managing any transaction on APIServerDaemon database
 * This class takes care of retrieving commands from ge_queue table
 * and update its status records values accordingly to the command
 * execution lifetime
 * Below the mapping of any possible (action,status) values in ge_queue table
 * ---------+------------------------------------------------------------
 * Action   | Statuses
 * ---------+------------------------------------------------------------
 * SUBMIT   | QUEUED|PROCESSING|PROCESSED|FAILED|DONE
 * GETSTATUS| This action never registers into ge_queue table
 *          | REST APIs directly returns the ge_queue state of the given task
 * GETOUTPUT| This action never registers into ge_queue table
 *          | REST APIs directly returns the ge_queue state of the given task
 * JOBCANCEL| QUEUED|PROCESSING|PROCESSED|FAILED|CANCELLED
 *
 * APIServerDaemon foresees two different thread loops:
 *  - APIServerDaemonPolling: This thread retrieves commands coming from
 *    the APIServerDaemon API Server REST calls processing only tasks in
 *    QUEUED state and leaves them in PROCESSING state once processed
 *  - APIServerDaeminController: This thread has the responsibility to verify
 *    the time consistency of any 'active' state in the queue and keep
 *    updated information about the real task state in accordance inside the
 *    underlying target architecture; for instance the GridEngine'
 *     ActiveGridInteraction (agi_id field) to check job status
 *    The controller thread is necessary because the any REST call related to
 *    job status or job output should ever return fresh information, thus
 *    the controller loop timely cross-check job status consistency between
 *    the as_queue and the targeted architecture; for instance the
 *    GridEngine' ActiveGridInteraction table. Each specific architecture
 *    change is in charge of the specific interface object
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
public class APIServerDaemonDB {

    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonDB.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");
    /**
     * DB (MySQL) Driver name.
     */
    private static String driverName = "com.mysql.jdbc.Driver";

    /*
     * DB connection settings
     */
    /**
     * Database name.
     */
    private String dbname = "";
    /**
     * Database host.
     */
    private String dbhost = "";
    /**
     * Database port number.
     */
    private String dbport = "";
    /**
     * Database user name.
     */
    private String dbuser = "";
    /**
     * Database password.
     */
    private String dbpass = "";
    /**
     * Database connection URL.
     */
    private String connectionURL = null;

    /*
     * DB variables
     */
    /**
     * MySQL driver Connection class.
     */
    private Connection connect = null;
    /**
     * MySQL driver Statement class.
     */
    private Statement statement = null;
    /**
     * MySQL driver PreparedStatement class.
     */
    private PreparedStatement preparedStatement = null;
    /**
     * MySQL driver ResultSet class.
     */
    private ResultSet resultSet = null;
    /**
     * Thread name.
     */
    private String threadName;

    /*
     * Constructors ...
     */

    /**
     * Empty constructor, it do not fill DB connection settings.
     */
    public APIServerDaemonDB() {
        threadName = Thread.currentThread().getName();
    }

    /**
     * Constructor that uses directly the JDBC connection URL.
     *
     * @param connURL - jdbc connection URL containing:
     *                        dbhost, dbport, dbuser, dbpass
     *                        and dbname in a single line
     */
    public APIServerDaemonDB(final String connURL) {
        this();
        this.connectionURL = connURL;
    }

    /**
     * Constructor that uses detailed connection settings used to buid the JDBC
     * connection URL.
     *
     * @param host - APIServerDaemon database hostname
     * @param port - APIServerDaemon database listening port
     * @param user - APIServerDaemon database user name
     * @param pass - APIServerDaemon database user password
     * @param name - APIServerDaemon database name
     */
    public APIServerDaemonDB(final String host,
                             final String port,
                             final String user,
                             final String pass,
                             final String name) {
        this();
        this.dbhost = host;
        this.dbport = port;
        this.dbuser = user;
        this.dbpass = pass;
        this.dbname = name;
        LOG.debug("DB:host='" + this.dbhost
                + "', port='" + this.dbport
                + "', user='" + this.dbuser
                + "', pass='" + this.dbpass
                + "', name='" + this.dbname);
        prepareConnectionURL();
    }

    /**
     * Close all db opened elements: resultset,statement,cursor,connection.
     *
     * public void close() { closeSQLActivity();
     *
     * try { if (connect != null) { _log.debug("closing connect");
     * connect.close(); connect = null; } } catch (Exception e) { _log.fatal(
     * "Unable to close DB: '" + this.connectionURL + "'");
     * _log.fatal(e.toString()); }
     *
     * _log.debug("Closed DB: '" + this.connectionURL + "'"); }
     */

    /**
     * Close all db opened elements except the connection.
     */
    public final void closeSQLActivity() {
        try {
            if (resultSet != null) {
                LOG.debug("closing resultSet");
                resultSet.close();
                resultSet = null;
            }

            if (statement != null) {
                LOG.debug("closing statement");
                statement.close();
                statement = null;
            }

            if (preparedStatement != null) {
                LOG.debug("closing preparedStatement");
                preparedStatement.close();
                preparedStatement = null;
            }

            if (connect != null) {
                LOG.debug("closing connect");
                connect.close();
                connect = null;
            }
        } catch (SQLException e) {
            LOG.fatal("Unable to close SQLActivities "
                    + "(resultSet, statement, preparedStatement,connect)");
            LOG.fatal(e.toString());
        }
    }

    /**
     * Connect to the APIServerDaemon database.
     *
     * @return connect object.
     */
    private boolean connect() {
        if (connect == null) {
            try {

                // Unnecessary due to registerDriver
                // Class.forName("com.mysql.jdbc.Driver");
                connect = DriverManager.getConnection(this.connectionURL);
                LOG.debug("Connected to DB: '" + this.connectionURL + "'");
            } catch (Exception e) {
                LOG.fatal("Unable to connect DB: '"
                        + this.connectionURL + "'");
                LOG.fatal(e.toString());
            }
        } else {
            LOG.debug("Connection object already present");
        }

        return (connect != null);
    }

    /**
     * Prepare a connectionURL from detailed connection settings.
     */
    private void prepareConnectionURL() {
        this.connectionURL = "jdbc:mysql://" + dbhost
                + ":" + dbport + "/"
                + dbname + "?user="
                + dbuser + "&password="
                + dbpass;
        LOG.debug("DBURL: '" + this.connectionURL + "'");
    }

    /**
     * Register the MySQL driver.
     */
    public static void registerDriver() {
        try {
            LOG.debug("Registering driver: '" + driverName + "'");
            Class.forName(driverName);
        } catch (ClassNotFoundException e) {
            LOG.fatal("Unable to unregister driver: '" + driverName + "'");
        }
    }

    /**
     * Unregister MySQL driver.
     */
    public static void unregisterDriver() {
        Enumeration<Driver> drivers = DriverManager.getDrivers();

        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();

            try {
                DriverManager.deregisterDriver(driver);
                LOG.info(
                        String.format("deregistering jdbc driver: %s",
                                driver));
            } catch (SQLException e) {
                LOG.fatal(
                        String.format("Error deregistering driver %s",
                                driver), e);
            }
        }
    }

    /**
     * update values of a given command except for: date fields and
     * action_info; creation date will be ignored, while last_change
     * will be set to now().
     *
     * @param command - Command extracted from queue
     * @see APIServerDaemonCommand
     * @throws SQLException SQL exception
     */
    public final void updateCommand(final APIServerDaemonCommand command)
            throws SQLException {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables as_queue write, task write;";
            statement = connect.createStatement();
            statement.execute(sql);

            // update command values into as_queue table
            sql = "update as_queue set target_id = ?" + LS
                + "                   ,status = ?" + LS
                + "                   ,target_status = ?" + LS
                + "                   ,last_change = now()" + LS
                + "where task_id=?" + LS + "  and action=?";
            int paramNum = 1;
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(paramNum++, command.getTargetId());
            preparedStatement.setString(paramNum++, command.getStatus());
            preparedStatement.setString(paramNum++, command.getTargetStatus());
            preparedStatement.setInt(paramNum++, command.getTaskId());
            preparedStatement.setString(paramNum++, command.getAction());
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;

            // Propagate command status in task table
            sql = "update task set status = ?" + LS
                + "               ,last_change = now()" + LS
                + "where id=?";

            String newStatus = ((command.getTargetStatus() == null)
                               ? "READY"
                               : command.getTargetStatus());

            LOG.debug("New task table status for task_id: '"
                    + command.getTaskId()
                    + "' is: '" + newStatus + "'");
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setString(1, newStatus);
            preparedStatement.setInt(2, command.getTaskId());
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;

            // Unlock ge_queue table
            sql = "unlock tables;";

            // statement=connect.createStatement();
            statement.execute(sql);
            LOG.debug("Updated command in as_queue and task tables: " + LS
                    + command);
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Return object' connection URL.
     *
     * @return APIServerDaemon database connection URL
     */
    public final String getConnectionURL() {
        return this.connectionURL;
    }

    /**
     * Retrieves available commands for the APIServer returning maxCmds
 records from the as_queue table Commands must be in QUEUED status while
 taken records will be flagged as PROCESSING Table as_queue will be
 locked to avoid any inconsistency in concurrent access.
     *
     * @param maxCmds
     *            Maximum number of records to get from the ge_queue table
     * @return List of APIServerDaemonCommand objects
     * @see APIServerDaemonCommand
     */
    public final List<APIServerDaemonCommand>
        getControllerCommands(final int maxCmds) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return null;
        }

        List<APIServerDaemonCommand> commandList = new ArrayList<>();

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables as_queue read;";
            statement = connect.createStatement();
            statement.execute(sql);
            sql = "select task_id" + LS
                    + "      ,target_id" + LS
                    + "      ,target" + LS
                    + "      ,action" + LS
                    + "      ,status" + LS
                    + "      ,target_status" + LS
                    + "      ,retry" + LS
                    + "      ,creation" + LS
                    + "      ,last_change" + LS
                    + "      ,check_ts" + LS
                    + "      ,action_info" + LS
                    + "from as_queue" + LS
                    + "where status = 'PROCESSING'" + LS
                    + "   or status = 'PROCESSED'" + LS
                    + "order by check_ts asc" + LS
                    + "limit ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, maxCmds);
            resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                APIServerDaemonCommand asdCmd =
                        new APIServerDaemonCommand(
                                connectionURL,
                                resultSet.getInt("task_id"),
                                resultSet.getInt("target_id"),
                                resultSet.getString("target"),
                                resultSet.getString("action"),
                                resultSet.getString("status"),
                                resultSet.getString("target_status"),
                                resultSet.getInt("retry"),
                                resultSet.getTimestamp("creation"),
                                resultSet.getTimestamp("last_change"),
                                resultSet.getTimestamp("check_ts"),
                                resultSet.getString("action_info"));
                commandList.add(asdCmd);
                LOG.debug("Loaded command: " + LS + asdCmd);
            }
            // Unlock task_output_file table
            sql = "unlock tables;";
            // statement=connect.createStatement();
            statement.execute(sql);
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
        return commandList;
    }

    /**
     * Get the current database version.
     * @return Database schema version number
     */
    public final String getDBVer() {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return null;
        }

        String dbVer = "";

        try {
            String sql;

            sql = "select max(version) as dbver from db_patches;";
            statement = connect.createStatement();
            resultSet = statement.executeQuery(sql);

            while (resultSet.next()) {
                dbVer = resultSet.getString("dbver");
                LOG.debug("DBVer: '" + dbVer + "'");
            }

            resultSet.close();
            resultSet = null;
            statement.close();
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return dbVer;
    }

    /**
     * Retrieves available commands for the APIServer returning maxCommands
     * records from the as_queue table Commands must be in QUEUED status while
     * taken records will be flagged as PROCESSING. Table as_queue will be
     * locked to avoid any inconsistency in concurrent access.
     *
     * @param maxCommands - Maximum number of records to get from the
     *                      as_queue table
     * @retun A list of APIServerDaemonCommand objects
     * @see APIServerDaemonCommand
     * @return List of APIServerDaemonCommand records
     */
    public final List<APIServerDaemonCommand>
        getQueuedCommands(final int maxCommands) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return null;
        }

        List<APIServerDaemonCommand> commandList = new ArrayList<>();

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables as_queue write;";
            statement = connect.createStatement();
            statement.execute(sql);
            sql = "select task_id" + LS
                    + "      ,target_id" + LS
                    + "      ,target" + LS
                    + "      ,action" + LS
                    + "      ,status" + LS
                    + "      ,target_status" + LS
                    + "      ,retry" + LS
                    + "      ,creation" + LS
                    + "      ,last_change" + LS
                    + "      ,check_ts" + LS
                    + "      ,action_info" + LS
                    + "from as_queue" + LS
                    + "where status = 'QUEUED'" + LS
                    + "order by last_change asc" + LS
                    + "limit ?" + LS
                    + ";";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, maxCommands);
            resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                APIServerDaemonCommand asdCmd =
                        new APIServerDaemonCommand(
                                connectionURL,
                                resultSet.getInt("task_id"),
                                resultSet.getInt("target_id"),
                                resultSet.getString("target"),
                                resultSet.getString("action"),
                                resultSet.getString("status"),
                                resultSet.getString("target_status"),
                                resultSet.getInt("retry"),
                                resultSet.getTimestamp("creation"),
                                resultSet.getTimestamp("last_change"),
                                resultSet.getTimestamp("check_ts"),
                                resultSet.getString("action_info"));

                commandList.add(asdCmd);
                LOG.debug("Loaded command: " + LS + asdCmd);
            }

            resultSet.close();
            resultSet = null;
            preparedStatement.close();

            // change status to the taken commands as PROCESSING
            Iterator<APIServerDaemonCommand> iterCmds = commandList.iterator();

            while (iterCmds.hasNext()) {
                APIServerDaemonCommand asCommand = iterCmds.next();

                sql = "update as_queue set status = 'PROCESSING'" + LS
                        + "                   ,last_change = now()" + LS
                        + "where task_id=?";
                preparedStatement = connect.prepareStatement(sql);
                preparedStatement.setInt(1, asCommand.getTaskId());
                preparedStatement.execute();
                preparedStatement.close();
                preparedStatement = null;
            }

            // Unlock ge_queue table
            sql = "unlock tables;";

            // statement=connect.createStatement();
            statement.execute(sql);
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return commandList;
    }

    /**
     * update values of a given command except for: date fields and action_
     * info; creation date will be ignored, while last_change will be set to
     * now().
     *
     * @param command - Extracted command from queue
     * @see APIServerCommand
     * @throws SQLException SQL exception
     */
    public final void checkUpdateCommand(final APIServerDaemonCommand command)
            throws SQLException {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables as_queue write, task write;";
            statement = connect.createStatement();
            statement.execute(sql);

            // update command values into as_queue table
            sql = "update as_queue set check_ts = now()" + LS
                + "where task_id=?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, command.getTaskId());
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;

            // Unlock ge_queue table
            sql = "unlock tables;";

            // statement=connect.createStatement();
            statement.execute(sql);
            LOG.debug("Updated check_ts in as_queue: " + LS + command);
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Delete any task entry from the API Server DB including the queue This
     * method does not delete entries related to the target executor interface
     * tables.
     * @param  taskId - task table identifier
     */
    final void removeTaksEntries(final int taskId) {
        if (!connect()) {
            LOG.fatal("Not connected to database");
            return;
        }

        try {
            String sql;
            //
            // Table task_output_file
            //
            // Lock ge_queue table first
            sql = "lock tables task_output_file write;";
            statement = connect.createStatement();
            statement.execute(sql);
            // Delete entries in task_output_file
            sql = "delete from task_output_file where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, taskId);
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;
            // Unlock task_output_file table
            sql = "unlock tables;";
            // statement=connect.createStatement();
            statement.execute(sql);
            //
            // Table task_input_file
            //
            // Lock task_input_file table first
            sql = "lock tables task_input_file write;";
            // statement=connect.createStatement();
            statement.execute(sql);
            // Delete entries in task_input_file
            sql = "delete from task_input_file where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, taskId);
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;
            // Unlock task_input_file table
            sql = "unlock tables;";
            // statement=connect.createStatement();
            statement.execute(sql);
            //
            // Table task_arguments
            //
            // Lock task_arguments table first
            sql = "lock tables task_arguments write;";
            // statement=connect.createStatement();
            statement.execute(sql);
            // Delete entries in ge_queue
            sql = "delete from task_arguments where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, taskId);
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;
            // Unlock task_arguments table
            sql = "unlock tables;";
            // statement=connect.createStatement();
            statement.execute(sql);
            //
            // Table runtime_data
            //
            // Lock runtime_data table first
            sql = "lock tables runtime_data write;";
            // statement=connect.createStatement();
            statement.execute(sql);
            // Delete entries in ge_queue
            sql = "delete from runtime_data where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, taskId);
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;
            // Unlock runtime_data table
            sql = "unlock tables;";
            // statement=connect.createStatement();
            statement.execute(sql);
            //
            // Table as_queue
            //
            // Lock ge_queue table first
            sql = "lock tables as_queue write;";
            // statement=connect.createStatement();
            statement.execute(sql);
            // Delete entries in ge_queue
            sql = "delete from as_queue where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, taskId);
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;
            // Unlock ge_queue table
            sql = "unlock tables;";
            // statement=connect.createStatement();
            statement.execute(sql);
            //
            // Table task
            //
            // Lock task table first
            sql = "lock tables task write;";
            // statement=connect.createStatement();
            statement.execute(sql);
            // Delete entries in task
            // !!! Removing tasks causes dangerous task_id recycle; using
            //     status update to 'PURGED' instead.
            //sql = "delete from task where id = ?;";
            sql = "update task set status = 'PURGED' where id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, taskId);
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;
            // Unlock task table
            sql = "unlock tables;";
            // statement=connect.createStatement();
            statement.execute(sql);
            LOG.debug("All entries for task '" + taskId
                    + "' have been removed");
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Retry the command setting values: status = QUEUED creation = now()
     * last_change = now() increase current retry.
     * @param asCommand - APIServerDaemonCommand extracted queue record
     */
    final void retryTask(final APIServerDaemonCommand asCommand) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables as_queue write;";
            statement = connect.createStatement();
            statement.execute(sql);

            // Delete entries in task_output_file
            sql = "update as_queue set " + LS
                    + "  status='QUEUED'" + LS
                    + " ,creation=now()" + LS
                    + " ,last_change=now()" + LS
                    + " ,retry=?" + LS
                    + "where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, asCommand.getRetry() + 1);
            preparedStatement.setInt(2, asCommand.getTaskId());
            preparedStatement.execute();

            // Unlock task_output_file table
            sql = "unlock tables;";
            statement.execute(sql);
            LOG.debug(
                    "Task '"
                  + asCommand.getTaskId()
                  + "' have been retried, attempt number: "
                  + asCommand.getRetry());
        } catch (SQLException e) {
            LOG.fatal("Unable to retry command with task_id"
                    + asCommand.getTaskId() + LS
                    + e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Trash the command setting values:
     * status = FAILED - polling loops will
     * never take it last_change = now().
     * @param asCommand - Queue command
     */
    final void trashTask(final APIServerDaemonCommand asCommand) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables as_queue write;";
            statement = connect.createStatement();
            statement.execute(sql);

            // Delete entries in task_output_file
            sql = "update as_queue set " + LS
                    + "  status='FAILED'" + LS
                    + " ,last_change=now()" + LS
                    + "where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, asCommand.getTaskId());
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;

            // Unlock task_output_file table
            sql = "unlock tables;";
            statement.execute(sql);
            LOG.debug("Task '" + asCommand.getTaskId() + "' has been trashed");
        } catch (SQLException e) {
            LOG.fatal("Unable to trash command with task_id"
                    + asCommand.getTaskId() + LS
                    + e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * update output paths of a given command.
     * @param command - APIServerDaemon queqe command
     * @param outputDir - Output directory path
     */
    final void updateOutputPaths(
            final APIServerDaemonCommand command,
            final String outputDir) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables task_output_file write;";
            statement = connect.createStatement();
            statement.execute(sql);
            sql = "update task_output_file set path = ?" + LS
                + "where task_id=?";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setString(1, command.getActionInfo()
                    + "/" + outputDir);
            preparedStatement.setInt(2, command.getTaskId());
            preparedStatement.execute();
            preparedStatement.close();
            preparedStatement = null;

            // Unlock ge_queue table
            sql = "unlock tables;";

            // statement=connect.createStatement();
            statement.execute(sql);
            LOG.debug("Output dir '"
                    + command.getActionInfo()
                    + "/" + outputDir
                    + "' updated");
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Set a keyName, keyValue, keyDesc record in runtime_data table.
     *
     * @param rtdKey - key name
     * @param rtdValue - key value
     * @param rtdDesc - key value
     * @param rtdProto -RunTime data access prototype: ftp, sftp, ...
     * @param rtdType - Type of target info: 'plain/text', ...
     * @param command - The associated APIServerDaemonCommand
     * @see APIServerDaemonCommand
     */
    public final void setRunTimeData(final String rtdKey,
                                     final String rtdValue,
                                     final String rtdDesc,
                                     final String rtdProto,
                                     final String rtdType,
                                     final APIServerDaemonCommand command) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            sql = "insert into runtime_data (task_id,    " + LS
                    + "                        data_id,    " + LS
                    + "                        data_name,  " + LS
                    + "                        data_value, " + LS
                    + "                        data_desc,  " + LS
                    + "                        data_proto, " + LS
                    + "                        data_type,  " + LS
                    + "                        creation,   " + LS
                    + "                        last_change)" + LS
                    + "select ?,(select if(max(data_id) is null," + LS
                    + "                    1," + LS
                    + "                    max(data_id)+1)" + LS
                    + "          from runtime_data rd" + LS
                    + "          where rd.task_id=?)," + LS
                    + "      ?,?,?,?,?,now(),now();";
            preparedStatement = connect.prepareStatement(sql);
            int paramNum = 1;
            preparedStatement.setInt(paramNum++, command.getTaskId());
            preparedStatement.setInt(paramNum++, command.getTaskId());
            preparedStatement.setString(paramNum++, rtdKey);
            preparedStatement.setString(paramNum++, rtdValue);
            preparedStatement.setString(paramNum++, rtdDesc);
            preparedStatement.setString(paramNum++, rtdProto);
            preparedStatement.setString(paramNum++, rtdType);
            preparedStatement.execute();
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Retrieve keyValue from a given runtime data keyName.
     * @return Return the runtime data record associated to the given key
     * @param rtdKey -  Runtime data key value
     * @param command -  The associated APIServerDaemon command record
     */
    final String getRunTimeData(
            final String rtdKey,
            final APIServerDaemonCommand command) {
        String rtdValue = "";
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return null;
        }

        String dbVer = "";

        try {
            String sql;

            sql = "select data_value "
                + "from runtime_data where task_id=? and data_name=?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, command.getTaskId());
            preparedStatement.setString(2, rtdKey);
            resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                rtdValue = resultSet.getString("data_value");
                LOG.debug("rtdValue: '" + rtdValue + "'");
            }

            resultSet.close();
            resultSet = null;
            preparedStatement.close();
            preparedStatement = null;
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return rtdValue;
    }

    /**
     * Get the command object related to the SUBMIT action stating from
     * a given task_id.
     * @return APIServerDaemonCommand object
     * @param taskId - Task record identifier
     */
    public final APIServerDaemonCommand getSubmitCommand(final int taskId) {
        APIServerDaemonCommand asdSubCmd = null;

        if (!connect()) {
            LOG.fatal("Not connected to database");
            return asdSubCmd;
        }

        try {
            int targetId;
            String targetName;
            String commandAction;
            String commandStatus;
            String targetStatus;
            int retryCount;
            Date commandCreation;
            Date commandChange;
            Date commandCheck;
            String commandInfo;
            String sql;

            sql = "select task_id," + LS
                + "       target_id," + LS
                + "       target," + LS
                + "       action," + LS
                + "       status," + LS
                + "       target_status," + LS
                + "       retry," + LS
                + "       creation," + LS
                + "       last_change," + LS
                + "       check_ts," + LS
                + "       action_info" + LS
                + "from as_queue " + LS
                + "where task_id = ?" + LS
                + "  and action = 'SUBMIT';";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, taskId);
            resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                targetId = resultSet.getInt("target_id");
                targetName = resultSet.getString("target");
                commandAction = resultSet.getString("action");
                commandStatus = resultSet.getString("status");
                targetStatus = resultSet.getString("target_status");
                retryCount = resultSet.getInt("retry");
                commandCreation = resultSet.getDate("creation");
                commandChange = resultSet.getDate("last_change");
                commandCheck = resultSet.getDate("check_ts");
                commandInfo = resultSet.getString("action_info");
                // Create the command
                asdSubCmd = new APIServerDaemonCommand(
                            connectionURL,
                            taskId,
                            targetId,
                            targetName,
                            commandAction,
                            commandStatus,
                            targetStatus,
                            retryCount,
                            commandCreation,
                            commandChange,
                            commandCheck,
                            commandInfo);
            }
            resultSet.close();
            resultSet = null;
            preparedStatement.close();
            preparedStatement = null;
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return asdSubCmd;
    }

}
