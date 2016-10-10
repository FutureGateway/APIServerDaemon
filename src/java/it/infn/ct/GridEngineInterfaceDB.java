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
 * This class interfaces the GridEngine userstracking database; it helps the
 * GridEngineInterface class.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 * @see GridEngineInterface
 */
public class GridEngineInterfaceDB {

    /*
     * Logger
     */
    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(GridEngineInterfaceDB.class.getName());
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
     * GridEngine UsersTracking DB
     */
    /**
     * GridEngine UsersTrackingDB database host.
     */
    private String utdbHost;
    /**
     * GridEngine UsersTrackingDB database port number.
     */
    private String utdbPort;
    /**
     * GridEngine UsersTrackingDB database user name.
     */
    private String utdbUser;
    /**
     * GridEngine UsersTrackingDB database password.
     */
    private String utdbPass;
    /**
     * GridEngine UsersTrackingDB database name.
     */
    private String utdbName;

    /**
     * Empty constructor for GridEngineInterface.
     */
    public GridEngineInterfaceDB() {
        LOG.debug("Initializing GridEngineInterfaceDB");
    }

    /**
     * Constructor that uses directly the JDBC connection URL.
     *
     * @param connURL - jdbc connection URL containing:
     *                  dbhost, dbport, dbuser, dbpass
     *                  and dbname in a single line
     */
    public GridEngineInterfaceDB(final String connURL) {
        this();
        LOG.debug("GridEngineInterfaceDB connection URL:" + LS + connURL);
        this.connectionURL = connURL;
    }

    /**
     * Initializing GridEngineInterface using userstrackingdb database
     * connection settings.
     *
     * @param host - UsersTrackingDB database hostname
     * @param port - UsersTrackingDB database listening port
     * @param user - UsersTrackingDB database user name
     * @param pass - UsersTrackingDB database user password
     * @param name - UsersTrackingDB database name
     */
    public GridEngineInterfaceDB(
            final String host,
            final String port,
            final String user,
            final String pass,
            final String name) {
        this();
        this.utdbHost = host;
        this.utdbPort = port;
        this.utdbUser = user;
        this.utdbPass = pass;
        this.utdbName = name;
        prepareConnectionURL();
    }

    /**
     * Close all db opened elements: resultset,statement,cursor,connection.
     *
     * public void close() { closeSQLActivity();
     *
     * try { if (connect != null) { connect.close(); connect = null; } } catch
     * (Exception e) {
     * _log.fatal("Unable to close DB: '" + this.connectionURL +
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
        this.connectionURL =
                  "jdbc:mysql://" + utdbHost
                + ":" + utdbPort
                + "/" + utdbName
                + "?user=" + utdbUser
                + "&password=" + utdbPass;
        LOG.debug("DBURL: '" + this.connectionURL + "'");
    }

    /**
     * Remove the given record form ActiveGridInteraction table.
     *
     * @param agiId - ActiveGridInteraction id
     */
    public final void removeAGIRecord(final int agiId) {
        if (!connect()) {
            LOG.fatal("Not connected to database");

            return;
        }

        try {
            String sql;

            sql = "delete from ActiveGridInteractions" + LS + "where id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, agiId);
            resultSet = preparedStatement.executeQuery();
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }
    }

    /**
     * Get ActiveGridInteraction' id field from given queue command.
     *
     * @param geCommand - Queue command
     * @return GridEngine UsersTracking DB ActiveGridInteraction record id
     */
    final int getAGIId(final APIServerDaemonCommand geCommand) {
        int agiId = 0;

        if (!connect()) {
            LOG.fatal("Not connected to database");

            return agiId;
        }

        try {
            String jobDesc = geCommand.getTaskId()
                    + "@" + geCommand.getActionInfo();
            String sql;

            sql = "select id" + LS + "from ActiveGridInteractions" + LS
                + "where user_description = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setString(1, jobDesc);
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                agiId = resultSet.getInt("id");
            }
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return agiId;
    }

    /**
     * Return object' connection URL.
     *
     * @return GridEngineDaemon database connection URL
     */
    public final String getConnectionURL() {
        return this.connectionURL;
    }

    /**
     * Get description of the given ActiveGridInteraction record.
     *
     * @param agiId - ActiveGridInteraction id
     * @return jobStatus
     */
    public final String getJobDescription(final int agiId) {
        String uderDesc = null;

        if (!connect()) {
            LOG.fatal("Not connected to database");

            return uderDesc;
        }

        try {
            String sql;

            sql = "select user_description" + LS
                + "from ActiveGridInteractions" + LS
                + "where id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, agiId);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            uderDesc = resultSet.getString("user_description");
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return uderDesc;
    }

    /**
     * Retrieve the job status.
     * @param agiId - UsersTracking ActiveGridInteraction record identifier
     * @return Job status
     */
    public final String getJobStatus(final int agiId) {
        String jobStatus = null;

        if (!connect()) {
            LOG.fatal("Not connected to database");

            return jobStatus;
        }

        try {
            String sql;

            sql = "select status" + LS
                + "from ActiveGridInteractions" + LS
                + "where id = ?;";
            preparedStatement = connect.prepareStatement(sql);
            preparedStatement.setInt(1, agiId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                jobStatus = resultSet.getString("status");
            }
        } catch (SQLException e) {
            LOG.fatal(e.toString());
        } finally {
            closeSQLActivity();
        }

        return jobStatus;
    }
}
