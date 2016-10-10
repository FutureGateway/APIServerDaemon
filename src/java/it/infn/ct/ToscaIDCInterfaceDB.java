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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.log4j.Logger;

/**
 * APIServerDaemon interface for TOSCA DB interface.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
public class ToscaIDCInterfaceDB {
    /*
     * Logger
     */
    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(ToscaIDCInterfaceDB.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");
    /**
     * APIServerDaemon database connection URL.
     */
    private String connectionURL = null;

    /*
     * DB variables
     */
    /**
     * MySQL interface conneciton class.
     */
    private Connection connect = null;
    /**
     * MySQL interface statemen class.
     */
    private Statement statement = null;
    /**
     * MySQL interface preparedStatement class.
     */
    private PreparedStatement preparedStatement = null;
    /**
     * MySQL interface resultSet class.
     */
    private ResultSet resultSet = null;

    /*
     * APIServerDaemon database
     */
    /**
     * APIServerDaemon database host.
     */
    private String asdbHost;
    /**
     * GridEngine UsersTrackingDB database port number.
     */
    private String asdbPort;
    /**
     * GridEngine UsersTrackingDB database user name.
     */
    private String asdbUser;
    /**
     * GridEngine UsersTrackingDB database password.
     */
    private String asdbPass;
    /**
     * GridEngine UsersTrackingDB database name.
     */
    private String asdbName;

    /**
     * Empty constructor for ToscaIDCInterfaceDB.
     */
    public ToscaIDCInterfaceDB() {
        LOG.debug("Initializing ToscaIDCInterfaceDB");
    }

    /**
     * Constructor that uses directly the JDBC connection URL.
     *
     * @param connURL - jdbc connection URL containing:
     *                  dbhost, dbport, dbuser, dbpass
     *                  and dbname in a single line
     */
    public ToscaIDCInterfaceDB(final String connURL) {
        this();
        LOG.debug("ToscaIDCInterfaceDB connection URL:"
                + LS + connURL);
        this.connectionURL = connURL;
    }

    /**
     * Initializing ToscaIDCInterfaceDB database database connection settings.
     *
     * @param host - APIServerDaemon database hostname
     * @param port - APIServerDaemon database listening port
     * @param user - APIServerDaemon database user name
     * @param pass - APIServerDaemon database user password
     * @param name - APIServerDaemon database name
     */
    public ToscaIDCInterfaceDB(final String host,
                               final String port,
                               final String user,
                               final String pass,
                               final String name) {
        this();
        this.asdbHost = host;
        this.asdbPort = port;
        this.asdbUser = user;
        this.asdbPass = pass;
        this.asdbName = name;
        prepareConnectionURL();
    }

    /**
     * Close all db opened elements: resultset,statement,cursor,connection
     *
     * public void close() { closeSQLActivity();
     *
     * try { if (connect != null) { connect.close(); connect = null; } } catch
     * (Exception e) { _log.fatal("Unable to close DB: '"
     * + this.connectionURL +
     * "'"); _log.fatal(e.toString()); }
     *
     * _log.info("Closed DB: '" + this.connectionURL + "'"); }
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
                    + "(resultSet, statement, preparedStatement, connect)");
            LOG.fatal(e.toString());
        }
    }

    /**
     * Connect to the GridEngineDaemon database.
     *
     * @return connect object
     */
    private boolean connect() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connect = DriverManager.getConnection(this.connectionURL);
        } catch (Exception e) {
            LOG.fatal("Unable to connect DB: '" + this.connectionURL + "'");
            LOG.fatal(e.toString());
        }

        LOG.debug("Connected to DB: '" + this.connectionURL + "'");

        return (connect != null);
    }

    /**
     * Prepare a connectionURL from detailed conneciton settings.
     */
    private void prepareConnectionURL() {
        this.connectionURL = "jdbc:mysql://" + asdbHost
                           + ":" + asdbPort
                           + "/" + asdbName
                           + "?user=" + asdbUser
                           + "&password=" + asdbPass;
        LOG.debug("ToscaIDCInterfaceDB connectionURL: '"
                + this.connectionURL + "'");
    }

    /**
     * Register the tId of the given toscaCommand.
     * @param toscaCommand - Queue command
     * @param toscaId - TOSCA UUID
     * @param toscaEndPoint - TOSCA orchestrator endpoint
     * @param status - Status of TOSCA deployment process
     * @return Tosca executor interface table record id
     */
    public final  int registerToscaId(
            final APIServerDaemonCommand toscaCommand,
            final String toscaId,
            final String toscaEndPoint,
            final String status) {
        int tId = 0;

        if (!connect()) {
            LOG.fatal("Not connected to database");

            return tId;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables tosca_idc write, tosca_idc as st read;";
            statement = connect.createStatement();
            statement.execute(sql);

            // Insert new entry for tosca_idc
            sql = "insert into tosca_idc (id,task_id," + LS
                    + " tosca_id," + LS
                    + " tosca_endpoint," + LS
                    + " tosca_status," + LS
                    + " creation," + LS
                    + " last_change)" + LS
                    + "select (select if(max(id)>0,max(id)+1,1)" + LS
                    + "from tosca_idc st),?,?,?,?,now(),now();";
            int paramNum = 1;
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(paramNum++, toscaCommand.getTaskId());
            preparedStatement.setString(paramNum++, toscaId);
            preparedStatement.setString(paramNum++, toscaEndPoint);
            preparedStatement.setString(paramNum++, status);
            preparedStatement.execute();

            // Get the new Id
            sql = "select id from tosca_idc where tosca_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setString(1, toscaId);
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                tId = resultSet.getInt("id");
            }

            // Unlock tables
            sql = "unlock tables;";
            statement.execute(sql);
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return tId;
    }

    /**
     * update the toscaId value into an existing tosca_idc record.
     *
     * @param toscaIDCId - The id record index in tosca_idc table
     * @param toscaUUID - tosca submission UUID field
     */
    public final void updateToscaId(final int toscaIDCId,
                                    final String toscaUUID) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables tosca_idc write;";
            statement = connect.createStatement();
            statement.execute(sql);

            // Insert new entry for simple tosca
            sql = "update tosca_idc set tosca_id=?," + LS
                + "                     tosca_status='SUBMITTED'," + LS
                + "                     creation=now()," + LS
                + "                     last_change=now()" + LS
                + "where id=?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setString(1, toscaUUID);
            preparedStatement.setInt(2, toscaIDCId);
            preparedStatement.execute();
            sql = "unlock tables;";
            statement.execute(sql);
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * update the tosca status value into an existing tosca_idc record.
     *
     * @param simpleToscaId - record index in tosca_idc table
     * @param toscaStatus - tosca submission status
     */
    public final void updateToscaStatus(final int simpleToscaId,
                                        final String toscaStatus) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            // Lock ge_queue table first
            sql = "lock tables tosca_idc write;";
            statement = connect.createStatement();
            statement.execute(sql);

            // Insert new entry for simple tosca
            sql = "update tosca_idc set tosca_status=?," + LS
                + "                     last_change=now() where id=?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setString(1, toscaStatus);
            preparedStatement.setInt(2, simpleToscaId);
            preparedStatement.execute();
            sql = "unlock tables;";
            statement.execute(sql);
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Return object' connection URL.
     *
     * @return ToscaIDCInterface database connection URL
     */
    public final String getConnectionURL() {
        return this.connectionURL;
    }

    /**
     * Get toscaId.
     *
     * @param toscaCommand - Queue command
     * @return toscaid
     */
    public final String getToscaId(final APIServerDaemonCommand toscaCommand) {
        String toscaId = "";

        if (!connect()) {
            LOG.fatal("Not connected to database");

            return toscaId;
        }

        try {
            String sql;

            sql = "select tosca_id" + LS + "from tosca_idc" + LS
                    + "where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, toscaCommand.getTaskId());
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                toscaId = resultSet.getString("tosca_id");
            }
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return toscaId;
    }

    /**
     * Get toscaEndPoint.
     *
     * @param toscaCommand - Queue command
     * @return TOSCA identifier
     */
    public final String toscaEndPoint(
            final APIServerDaemonCommand toscaCommand) {
        String toscaEndPoint = "";

        if (!connect()) {
            LOG.fatal("Not connected to database");

            return toscaEndPoint;
        }

        try {
            String sql;

            sql = "select tosca_endpoint" + LS
                + "from tosca_idc" + LS
                + "where task_id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, toscaCommand.getTaskId());
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                toscaEndPoint = resultSet.getString("tosca_endpoint");
            }
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return toscaEndPoint;
    }

    /**
     * Retrieve session token from the given command looking up to.
     *
     * @param toscaCommand - Queue command
     * @return command session token
     */
    final String getToken(final APIServerDaemonCommand toscaCommand) {
        String token = "";

        if (!connect()) {
            LOG.fatal("Not connected to database");
            return token;
        }

        try {
            String sql;

            sql = "select tk.token" + LS
                    + "from as_queue aq," + LS
                    + "     task t," + LS
                    + "     fg_token tk" + LS
                    + "where aq.task_id=t.id" + LS
                    + "  and tk.user_id = (select id" + LS
                    + "                    from fg_user u" + LS
                    + "                    where u.name=t.user)" + LS
                    + "  and aq.task_id=?" + LS
                    + "order by tk.creation desc limit 1;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, toscaCommand.getTaskId());
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                token = resultSet.getString("tk.token");
            }
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return token;
    }

}
