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
 * APIServerDaemon interface for TOSCA DB interface
 * @author brunor
 */
public class ToscaIDCInterfaceDB {
    /*
     * Logger
     */
    private static final Logger _log          = Logger.getLogger(SimpleToscaInterfaceDB.class.getName());
    public static final String  LS            = System.getProperty("line.separator");
    private String              connectionURL = null;

    /*
     * DB variables
     */
    private Connection        connect           = null;
    private Statement         statement         = null;
    private PreparedStatement preparedStatement = null;
    private ResultSet         resultSet         = null;

    /*
     * GridEngine UsersTracking DB
     */
    private String asdb_host;
    private String asdb_port;
    private String asdb_user;
    private String asdb_pass;
    private String asdb_name;

    /**
     * Empty constructor for SimpleToscaInterface
     */
    public ToscaIDCInterfaceDB() {
        _log.debug("Initializing SimpleToscaInterfaceDB");
    }

    /**
     * Constructor that uses directly the JDBC connection URL
     * @param connectionURL jdbc connection URL containing: dbhost, dbport,
     * dbuser, dbpass and dbname in a single line
     */
    public ToscaIDCInterfaceDB(String connectionURL) {
        this();
        _log.debug("SimpleTosca connection URL:" + LS + connectionURL);
        this.connectionURL = connectionURL;
    }

    /**
     * Initializing SimpleToscaInterface database
     * database connection settings
     * @param asdb_host APIServerDaemon database hostname
     * @param asdb_port APIServerDaemon database listening port
     * @param asdb_user APIServerDaemon database user name
     * @param asdb_pass APIServerDaemon database user password
     * @param asdb_name APIServerDaemon database name
     */
    public ToscaIDCInterfaceDB(String asdb_host, String asdb_port, String asdb_user, String asdb_pass,
                                  String asdb_name) {
        this();
        this.asdb_host = asdb_host;
        this.asdb_port = asdb_port;
        this.asdb_user = asdb_user;
        this.asdb_pass = asdb_pass;
        this.asdb_name = asdb_name;
        prepareConnectionURL();
    }

    /**
     * Close all db opened elements: resultset,statement,cursor,connection
     */
    public void close() {
        closeSQLActivity();

        try {
            if (connect != null) {
                connect.close();
                connect = null;
            }
        } catch (Exception e) {
            _log.fatal("Unable to close DB: '" + this.connectionURL + "'");
            _log.fatal(e.toString());
        }

        _log.info("Closed DB: '" + this.connectionURL + "'");
    }

    /**
     * Close all db opened elements except the connection
     */
    public void closeSQLActivity() {
        try {
            if (resultSet != null) {
                _log.debug("closing resultSet");
                resultSet.close();
                resultSet = null;
            }

            if (statement != null) {
                _log.debug("closing statement");
                statement.close();
                statement = null;
            }

            if (preparedStatement != null) {
                _log.debug("closing preparedStatement");
                preparedStatement.close();
                preparedStatement = null;
            }
        } catch (SQLException e) {
            _log.fatal("Unable to close SQLActivities (resultSet, statement, preparedStatement)");
            _log.fatal(e.toString());
        }
    }

    /**
     * Connect to the GridEngineDaemon database
     * @return connect object
     */
    private boolean connect() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connect = DriverManager.getConnection(this.connectionURL);
        } catch (Exception e) {
            _log.fatal("Unable to connect DB: '" + this.connectionURL + "'");
            _log.fatal(e.toString());
        }

        _log.debug("Connected to DB: '" + this.connectionURL + "'");

        return (connect != null);
    }

    /**
     * Prepare a connectionURL from detailed conneciton settings
     */
    private void prepareConnectionURL() {
        this.connectionURL = "jdbc:mysql://" + asdb_host + ":" + asdb_port + "/" + asdb_name + "?user=" + asdb_user
                             + "&password=" + asdb_pass;
        _log.debug("SimpleToscaInterface connectionURL: '" + this.connectionURL + "'");
    }
    
}