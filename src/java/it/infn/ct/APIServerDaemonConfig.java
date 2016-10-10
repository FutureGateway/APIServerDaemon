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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.log4j.Logger;

/**
 * Class that contain all APIServerDaemon configuration settings It manages a
 * configuration file and/or static settings.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 * @see APIServerDaemon
 */
public class APIServerDaemonConfig {
    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonConfig.class.getName());

    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");

    /**
     * Maximum number of execution threads default value.
     */
    private static final int DEFAULTMAXTHREADS = 100;

    /**
     * Number of seconds before termination when closing threads default value.
     */
    private static final int DEFAULTCLOSETIMEOUT = 20;

    /**
     * Number of milliseconds for queue polling processing loop cycle delay.
     */
    private static final int DEFAULTPOLLINGDELAY = 4000;

    /**
     * Default maximum number of commands extracted from queue.
     */
    private static final int DEFAULTPOLLINGMAXCOMMANDS = 5;

    /**
     * Number of milliseconds for queue controller processing loop cycle delay.
     */
    private static final int DEFAULTCONTROLLERDELAY = 10000;
    /**
     * Default maximum number of commands extracted from queue.
     */
    private static final int DEFAULTCONTROLLMAXCOMMANDS = 5;

    /**
     * Defaul maximum number of queue command retries.
     */
    private static final int DEFAULTTASKMAXRETRIES = 5;
    /**
     * Default number of milliseconds for a command execution retry.
     */
    private static final int DEFAULTTASKMAXWAIT = 1800000; // 30 miniyrd

    /**
     * APIServerDaemon configuration file.
     */
    private final String asdPropetiesFile = "APIServerDaemon.properties";
    /*
     * Database settings
     */
    /**
     * APIServerDaemon database host.
     */
    private String apisrvDBHost = "localhost";
    /**
     * APIServerDaemon database port.
     */
    private String apisrvDBPort = "3306";
    /**
     * APIServerDaemon database user name.
     */
    private String apisrvDBUser = "fgapiserver";
    /**
     * APIServerDaemon database password.
     */
    private String apisrvDBPass = "fgapiserver_password";
    /**
     * APIServerDaemon database name.
     */
    private String apisrvDBName = "fgapiserver";
    /**
     * APIServerDaemon database chema version.
     */
    private String apisrvDBVer = "";

    /*
     * GrigEngineDaemon settings
     */
    /**APIServerDaemon maximum number of threads.
     */
    private int asdMaxThreads = DEFAULTMAXTHREADS;
    /**
     * APIServerDaemon number of secondos before terminate deamons.
     */
    private int asdCloseTimeout = DEFAULTCLOSETIMEOUT;

    /**
     * GridEngineDaemonPolling default polling loop delay value.
     */
    private int asPollingDelay = DEFAULTPOLLINGDELAY;
    /**
     * GridEngineDaemonPolling default maxumum number of commands to extract.
     */
    private int asPollingMaxCommands = DEFAULTPOLLINGMAXCOMMANDS;

    /*
     * GridEngineDaemonController settings
     */
    /**
     * GridEngineDaemonPolling default controller loop delay value.
     */
    private int asControllerDelay = DEFAULTCONTROLLERDELAY;
    /**
     * GridEngineDaemonPolling default maxumum number of commands to control.
     */
    private int asControllerMaxCommands = DEFAULTCONTROLLMAXCOMMANDS;

    /*
     * GridEngineDaemon task retry policies
     */
    /**
     * APIServerDaemon default maximum number of queue command retries.
     */
    private int asTaskMaxRetries = DEFAULTTASKMAXRETRIES;
    /**
     * APIServerDaemon default number of milliseconds for a command retry.
     */
    private int asTaskMaxWait = DEFAULTTASKMAXWAIT;

    /*
     * GridEngine UsersTracking DB
     */
    /**
     * GridEngine' UsersTrackingDB connection pool name.
     */
    private String utdbJNDI = "jdbc/UserTrackingPool";
    /**
     * GridEngine' UsersTrackingDB database name.
     */
    private String utdbHost = "localhost";
    /**
     * GridEngine' UsersTrackingDB database port number.
     */
    private String utdbPort = "3306";
    /**
     * GridEngine' UsersTrackingDB database user name.
     */
    private String utdbUser = "tracking_user";
    /**
     * GridEngine' UsersTrackingDB database password.
     */
    private String utdbPass = "usertracking";
    /**
     * GridEngine' UsersTrackingDB database name.
     */
    private String utdbName = "userstracking";

    /**
     * Load the given configuration file which overrides static settings.
     *
     * @param showConf - when true shows loaded configuration parameters
     */
    public APIServerDaemonConfig(final boolean showConf) {
        /*
         * Load a configuration file containing APIServerDaemon settings wich
         * override the static settings defined in the class
         */
        loadProperties();
        if (showConf) {
            LOG.info("APIServerDaemon config:" + LS + this.toString());
        }
    }

    /**
     * Load APIServerDaemon.properties values.
     */
    private void loadProperties() {
        InputStream inputStream = null;
        Properties prop = new Properties();
        try {
            inputStream = this.getClass().getResourceAsStream(asdPropetiesFile);

            prop.load(inputStream);

            /*
             * Retrieving configuration values
             */

            // APIServer DB settings
            String propApiSrvDBHost = prop.getProperty("apisrv_dbhost");
            String propApiSrvDBPort = prop.getProperty("apisrv_dport");
            String propApiSrvDBUser = prop.getProperty("apisrv_dbuser");
            String propApiSrvDBPass = prop.getProperty("apisrv_dbpass");
            String propApiSrvDBName = prop.getProperty("apisrv_dbname");
            String propApiSrvDBVer = prop.getProperty("apisrv_dbver");

            // GridEngineDaemon thread settings
            String propASMaxThreads = prop.getProperty("asdMaxThreads");
            String propASCloseTimeout = prop.getProperty("asdCloseTimeout");

            // GridEngineDaemonPolling settings
            String propASPollingDelay = prop.getProperty("asPollingDelay");
            String propASPollingMaxCommands =
                    prop.getProperty("asPollingMaxCommands");

            // GridEngineDaemonController settings
            String propASControllerDelay =
                    prop.getProperty("asControllerDelay");
            String propASControllerMaxCommands =
                    prop.getProperty("asControllerMaxCommands");

            // GridEngineDaemon retry policies
            String propASTaskMaxRetries = prop.getProperty("asTaskMaxRetries");
            String propASTaskMaxWait = prop.getProperty("asTaskMaxWait");

            // GridEngine' UsersTracking database settings
            String propUTDBjndi = prop.getProperty("utdb_jndi");
            String propUTDBhost = prop.getProperty("utdb_host");
            String propUTDBport = prop.getProperty("utdb_port");
            String propUTDBuser = prop.getProperty("utdb_user");
            String propUTDBpass = prop.getProperty("utdb_pass");
            String propUTDBname = prop.getProperty("utdb_name");

            /*
             * Override or use class' settings
             */

            // APIServer DB settings
            if (propApiSrvDBHost != null) {
                this.apisrvDBHost = propApiSrvDBHost;
            }
            if (propApiSrvDBPort != null) {
                this.apisrvDBPort = propApiSrvDBPort;
            }
            if (propApiSrvDBUser != null) {
                this.apisrvDBUser = propApiSrvDBUser;
            }
            if (propApiSrvDBPass != null) {
                this.apisrvDBPass = propApiSrvDBPass;
            }
            if (propApiSrvDBName != null) {
                this.apisrvDBName = propApiSrvDBName;
            }
            if (propApiSrvDBVer != null) {
                this.apisrvDBVer = propApiSrvDBVer;
            }

            // APIServerDaemon thread settings
            if (propASMaxThreads != null) {
                this.asdMaxThreads = Integer.parseInt(propASMaxThreads);
            }
            if (propASCloseTimeout != null) {
                this.asdCloseTimeout = Integer.parseInt(propASCloseTimeout);
            }

            // APIServerDaemonPolling settings
            if (propASPollingDelay != null) {
                this.asPollingDelay = Integer.parseInt(propASPollingDelay);
            }
            if (propASPollingMaxCommands != null) {
                this.asPollingMaxCommands =
                        Integer.parseInt(propASPollingMaxCommands);
            }

            // APIServerDaemonController settings
            if (propASControllerDelay != null) {
                this.asControllerDelay = Integer.parseInt(
                        propASControllerDelay);
            }
            if (propASControllerMaxCommands != null) {
                this.asControllerMaxCommands =
                        Integer.parseInt(propASControllerMaxCommands);
            }

            // APIServerDaemon task retry policies
            if (propASTaskMaxRetries != null) {
                this.asTaskMaxRetries = Integer.parseInt(propASTaskMaxRetries);
            }
            if (propASTaskMaxWait != null) {
                this.asTaskMaxWait = Integer.parseInt(propASTaskMaxWait);
            }

            // GridEngine' UsersTracking database settings
            if (propUTDBjndi != null) {
                this.utdbJNDI = propUTDBjndi;
            }
            if (propUTDBhost != null) {
                this.utdbHost = propUTDBhost;
            }
            if (propUTDBport != null) {
                this.utdbPort = propUTDBport;
            }
            if (propUTDBuser != null) {
                this.utdbUser = propUTDBuser;
            }
            if (propUTDBpass != null) {
                this.utdbPass = propUTDBpass;
            }
            if (propUTDBname != null) {
                this.utdbName = propUTDBname;
            }
        } catch (NullPointerException e) {
            LOG.warn("Unable to load property file; using default settings");
        } catch (IOException e) {
            LOG.warn("Error reading file: " + e);
        } catch (NumberFormatException e) {
            LOG.warn("Error while reading property file: " + e);
        } finally {
            try {
                if (null != inputStream) {
                    inputStream.close();
                }
            } catch (IOException e) {
                System.out.println(
                        "Error closing configuration file input stream");
            }
        }
    }

    /*
     * Get and set methods ...
     */

    /*
     * Database settings
     */

    /**
     * Prepare a connectionURL from detailed connection settings.
     * @return connectionURL from detailed connection settings
     */
    public final String getApisrvURL() {
        String asConnURL = "jdbc:mysql://" + getApisrvDBHost()
                + ":" + getApisrvDBPort()
                + "/" + getApisrvDBName()
                + "?user=" + getApisrvDBUser()
                + "&password=" + getApisrvDBPass();
        LOG.debug("APIServerDB ConnectionURL: '" + asConnURL + "'");
        return asConnURL;
    }

    /**
     * Get APIServerDaemon database name.
     *
     * @return apisrvDBName
     */
    public final String getApisrvDBName() {
        return apisrvDBName;
    }

    /**
     * Set APIServer database name.
     *
     * @param dbname APIServer database name
     */
    public final void setApisrvDBName(final String dbname) {
        this.apisrvDBName = dbname;
    }

    /**
     * Get APIServerDaemon database host.
     *
     * @return apisrvDBHost
     */
    public final String getApisrvDBHost() {
        return apisrvDBHost;
    }

    /**
     * Set APIServer database host.
     *
     * @param dbhost - Database host name
     */
    public final void setApisrvDBHost(final String dbhost) {
        this.apisrvDBHost = dbhost;
    }

    /**
     * Get APIServer database port.
     *
     * @return apisrvDBPort
     */
    public final String getApisrvDBPort() {
        return apisrvDBPort;
    }

    /**
     * Set APIServer database port.
     *
     * @param dbport - Database port number
     */
    public final void setApisrvDBPort(final String dbport) {
        this.apisrvDBPort = dbport;
    }

    /**
     * Get APIServer database user.
     *
     * @return apisrvDBUser - Database user name
     */
    public final String getApisrvDBUser() {
        return apisrvDBUser;
    }

    /**
     * Set APIServer database user.
     *
     * @param dbuser - Database user name
     */
    public final void setApisrvDBUser(final String dbuser) {
        this.apisrvDBUser = dbuser;
    }

    /**
     * Get APIServer database password.
     *
     * @return apisrv_pass
     */
    public final String getApisrvDBPass() {
        return apisrvDBPass;
    }

    /**
     * Set APIServer database password.
     *
     * @param dbpass - Database password
     */
    public final void setApisrvDBPass(final String dbpass) {
        this.apisrvDBPass = dbpass;
    }

    /**
     * Get APIServerDaemon thread closure timeout.
     *
     * @return asdCloseTimeout number of seconds waiting for thread closure
     */
    public final int getASDCloseTimeout() {
        return asdCloseTimeout;
    }

    /**
     * Set APIServerDaemon thread closure timeout.
     *
     * @param closeTimeout - Threadas closuew timeout
     */
    public final void setASDCloseTimeout(final int closeTimeout) {
        this.asdCloseTimeout = closeTimeout;
    }

    /**
     * Get APIServerDaemon database version.
     *
     * @return apisrvDBVer
     */
    public final String getASDBVer() {
        return apisrvDBVer;
    }

    /**
     * Set APIServer database version.
     *
     * @param dbver - Database schema version
     */
    public final void setASDBver(final String dbver) {
        this.apisrvDBVer = dbver;
    }

    /*
     * APIServerDaemon
     */

    /**
     * Get APIServerDaemon max number of threads.
     *
     * @return asdMaxThreads maximum number of threads
     */
    public final int getMaxThreads() {
    return asdMaxThreads;
    }

    /**
     * Set APIServerDaemon max number of threads.
     *
     * @param gedMaxThreads - Maximum number of threads
     */
    public final void setMaxThreads(final int gedMaxThreads) {
        this.asdMaxThreads = gedMaxThreads;
    }

    /**
     * Get APIServerDaemon closing thread timeout value.
     *
     * @return asdMaxThreads maximum number of threads
     */
    public final int getCloseTimeout() {
        return asdCloseTimeout;
    }

    /**
     * Set APIServerDaemon closing thread timeout value.
     *
     * @param closeTimeout closing thread timeout value
     */
    public final void setCloseTimeout(final int closeTimeout) {
        this.asdCloseTimeout = closeTimeout;
    }

    /*
     * APIServerDaemonPolling settings
     */

    /**
     * Get polling thread loop delay.
     *
     * @return asPollingDelay number of seconds for each controller loop
     */
    public final int getPollingDelay() {
        return asPollingDelay;
    }

    /**
     * Set polling thread loop delay.
     *
     * @param pollingDelay - Polling delay interval
     */
    public final void setPollingDelay(final int pollingDelay) {
        this.asPollingDelay = pollingDelay;
    }

    /**
     * Get polling thread max number of commands per loop.
     *
     * @return asPollingMaxCommands number of records to be extracted from
     *         ge_queue table
     */
    public final int getPollingMaxCommands() {
        return asPollingMaxCommands;
    }

    /**
     * Set polling thread max number of commands per loop.
     *
     * @param pollingMaxCommands - Maximum number of commands for single poll
     */
    public final void setPollingMaxCommands(final int pollingMaxCommands) {
        this.asPollingMaxCommands = pollingMaxCommands;
    }

    /*
     * APIServerDaemonController settings
     */

    /**
     * Get controller thread loop delay.
     *
     * @return asControllerDelay number of seconds for each controller loop
     */
    public final int getControllerDelay() {
        return asControllerDelay;
    }

    /**
     * Set controller thread loop delay.
     *
     * @param controllerDelay - Controller delay time
     */
    public final void setControllerDelay(final int controllerDelay) {
        this.asControllerDelay = controllerDelay;
    }

    /**
     * Get controller thread max number of commands per loop.
     *
     * @return asControllerMaxCommands number of records to be extracted from
     *         ge_queue table
     */
    public final int getControllerMaxCommands() {
        return asControllerMaxCommands;
    }

    /**
     * Set controller thread max number of commands per loop.
     *
     * @param controllerMaxCmds - Max number of controlled records
     */
    public final void setControllerMaxCommands(final int controllerMaxCmds) {
        this.asControllerMaxCommands = controllerMaxCmds;
    }

    /**
     * GridEngine jndi database resource.
     *
     * @return usertracking jndi resource name
     */
    final String getGridEngineDBjndi() {
        return this.utdbJNDI;
    }

    /**
     * GridEngine jdni database resource.
     *
     * @param jndi - jndi resource name
     */
    final void setGridEngineDBjndi(final String jndi) {
        this.utdbJNDI = jndi;
    }

    /**
     * Return the GridEngine' userstracking database host.
     *
     * @return userstracking database host
     */
    final String getGridEngineDBhost() {
        return this.utdbHost;
    }

    /**
     * Set the GridEngine' userstracking database host.
     *
     * @param host - userstracking database host
     */
    final void setGridEngineDBHost(final String host) {
        this.utdbHost = host;
    }

    /**
     * Return the GridEngine' userstracking database port.
     *
     * @return userstracking database port
     */
    final String getGridEngineDBPort() {
        return this.utdbPort;
    }

    /**
     * Set the GridEngine' userstracking database port.
     *
     * @param port - userstracking - database port
     */
    final void setGridEngineDBPort(final  String port) {
        this.utdbPort = port;
    }

    /**
     * Return the GridEngine' userstracking database user.
     *
     * @return userstracking database user
     */
    final String getGridEngineDBuser() {
        return this.utdbUser;
    }

    /**
     * Set the GridEngine' userstracking database user.
     *
     * @param user - userstracking database user
     */
    final void setGridEngineDBUser(final String user) {
        this.utdbUser = user;
    }

    /**
     * Return the GridEngine' userstracking database password.
     *
     * @return userstracking database password
     */
    final String getGridEngineDBPass() {
    return this.utdbPass;
    }

    /**
     * Set the GridEngine' userstracking database password.
     *
     * @param pass - userstracking database password
     */
    final void setGridEngineDBPass(final String pass) {
        this.utdbPass = pass;
    }

    /**
     * Return the GridEngine' userstracking database name.
     *
     * @return userstracking database name
     */
    final String getGridEngineDBName() {
        return this.utdbName;
    }

    /**
     * Set the GridEngine' userstracking database name.
     *
     * @param name - userstracking database name
     */
    final void setGridEngineDBName(final String name) {
        this.utdbName = name;
    }

    /*
     * APIServerDaemon task retry policies
     */

    /**
     * Return the maximum number of retries for a task request.
     * @return Maximum number of command retries
     */
    final int getTaskMaxRetries() {
        return this.asTaskMaxRetries;
    }

    /**
     * Return maximum number of seconds before to try a task retry.
     * @return Number of seconds before to try a task retry
     */
    final int getTaskMaxWait() {
        return this.asTaskMaxWait;
    }

    /**
     * Set the maximum number of retries for a task request.
     *
     * @param maxRetries - maximum number of retries for a task request
     */
    final void setTaskMaxRetries(final int maxRetries) {
        this.asTaskMaxRetries = maxRetries;
    }

    /**
     * Set the maximum number of seconds before to try a task retry.
     *
     * @param maxWait - maximum number of seconds before to try a task retry
     */
    final void setTaskMaxWait(final int maxWait) {
        this.asTaskMaxWait = maxWait;
    }

    /**
     * View configuration settings.
     * @return String serialization of APIServerDaemonConfig class values
     */
    @Override
    public final String toString() {
        /*
         * Database settings
         */
        return ""
        + "[API Server DB settings]" + LS
        + "    db_host : '" + apisrvDBHost + "'" + LS
        + "    db_port : '" + apisrvDBPort + "'" + LS
        + "    db_user : '" + apisrvDBUser + "'" + LS
        + "    db_pass : '" + apisrvDBPass + "'" + LS
        + "    db_name : '" + apisrvDBName + "'" + LS
        + "[APIServerDaemon settings]" + "'" + LS
        + "    asdMaxThreads   : '" + asdMaxThreads + "'" + LS
        + "    asdCloseTimeout : '" + asdCloseTimeout + "'" + LS
        + "[APIServerDaemonPolling settings]" + "'" + LS
        + "    asPollingDelay       : '" + asPollingDelay + "'" + LS
        + "    asPollingMaxCommands : '" + asPollingMaxCommands + "'" + LS
        + "[APIServerDaemonController settings]" + "'" + LS
        + "    asControllerDelay       : '" + asControllerDelay + "'" + LS
        + "    asControllerMaxCommands : '" + asControllerMaxCommands + "'" + LS
        + "[APIServerDaemon task retry policies]" + LS
        + "    asTaskMaxRetries  : '" + asTaskMaxRetries + "'" + LS
        + "    asTaskMaxWait     : '" + asTaskMaxWait + "'" + LS
        + "[GridEngine UsersTracking DB settings]" + LS
        + "    db_jndi : '" + utdbJNDI + "'" + LS
        + "    db_host : '" + utdbHost + "'" + LS
        + "    db_port : '" + utdbPort + "'" + LS
        + "    db_user : '" + utdbUser + "'" + LS
        + "    db_pass : '" + utdbPass + "'" + LS
        + "    db_name : '" + utdbName + "'" + LS;
    }
}
