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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

//import java.util.logging.Logger;
import org.apache.log4j.Logger;

/**
 * APIServerDaemon class instantiates the threadPool daemon and its main polling
 * thread.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
public class APIServerDaemon {

    /**
     * Logger object.
     */
    private static final Logger LOG = Logger.getLogger(APIServerDaemon.
                                                       class.getName());
    /**
     * Line separator constant.
     */
    private static final String LS = System.getProperty("line.separator");

    /**
     * Maximum number of execution threads default value.
     */
    private static final int DEFAULTMAXTHREADS = 100;

    /**
     * Number of seconds before termination when closing threads default value.
     */
    private static final int DEFAULTCLOSETIMEOUT = 20;

    /**
     * Maximum number of execution threads.
     */
    private int asdMaxThreads = DEFAULTMAXTHREADS;

    /**
     * Number of seconds before termination when closing threads.
     */
    private int asdCloseTimeout = DEFAULTCLOSETIMEOUT;

    /**
     * Executor thread pool.
     */
    private ExecutorService asdExecutor = null;

    /*
     * API Server Database settings; these class specific settings are
     * overridden by configuration file.
     */

    /**
     * APIServer daemon database name.
     */
    private String apisrvDBName;

    /**
     * APIServer daemon database hostname/ip address.
     */
    private String apisrvDBHost;

    /**
     * APIServer daemon database port number.
     */
    private String apisrvDBPort;

    /**
     * APIServer daemon database user name.
     */
    private String apisrvDBUser;

    /**
     * APIServer daemon database password.
     */
    private String apisrvDBPass;

    /**
     * APIServer daemon database schema version.
     */
    private String apisrvDBVer;

    /*
     * Queue controller and polling thread classes
     */

    /**
     * APIServer daemon queue polling thread class.
     */
    private APIServerDaemonPolling asdPolling;

    /**
     * APIServer daemon queue controller thread class.
     */
    private APIServerDaemonController asdController;

    /**
     * APIServerDaemon configuration.
     */
    private APIServerDaemonConfig asdConfig;

    /**
     * Class constructor, called by the ServletListener upon startup.
     */
    public APIServerDaemon() {

        // Load static configuration
        LOG.debug("Loading preferences for APIServerDaemon");
        asdConfig = new APIServerDaemonConfig(true);

        // Set configuration values for this class
        this.apisrvDBHost = asdConfig.getApisrvDBHost();
        this.apisrvDBPort = asdConfig.getApisrvDBPort();
        this.apisrvDBUser = asdConfig.getApisrvDBUser();
        this.apisrvDBPass = asdConfig.getApisrvDBPass();
        this.apisrvDBName = asdConfig.getApisrvDBName();
        this.apisrvDBVer = asdConfig.getASDBVer();

        // Load APIServerDaemon settings
        this.asdMaxThreads = asdConfig.getMaxThreads();
        this.asdCloseTimeout = asdConfig.getCloseTimeout();
        LOG.debug("API Server daemon config:" + LS
                 + "  [Database]" + LS
                 + "    db_host: '" + this.apisrvDBHost + "'"
                 + LS + "    db_port: '" + this.apisrvDBPort + "'"
                 + LS + "    db_user: '" + this.apisrvDBUser + "'"
                 + LS + "    db_pass: '" + this.apisrvDBPass + "'"
                 + LS + "    db_name: '" + this.apisrvDBName + "'"
                 + LS + "    db_ver : '" + this.apisrvDBVer + "'"
                 + LS
                 + "  [ThreadPool config]" + LS
                 + "    gedMaxThreads  : '" + this.asdMaxThreads + "'" + LS
                 + "    gedCloseTimeout: '"
                 + this.asdCloseTimeout + "'" + LS);
    }

    /**
     * Terminate the thread pool and its threads.
     */
    final void shutdown() {
        asdPolling.terminate();
        LOG.info("Terminated polling thread");

        try {
            asdExecutor.shutdown();
            asdExecutor.awaitTermination(asdCloseTimeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.info("APIServer Daemon polling termination cancelled");
        } finally {
            if (!asdExecutor.isTerminated()) {
                LOG.warn("Thread pool closure not finished");
            }

            asdExecutor.shutdownNow();
            LOG.warn("APIServer Daemon forcing termination");
        }

        LOG.info("APIServer Daemon terminated");
    }

    /**
     * Initialize the APIServer daemon Threadpool and its main polling loop.
     */
    final void startup() {
        LOG.info("Initializing APIServer Daemon");

        /*
         * Before to start the daemon, verify that all conditions are satisfied
         */

        // Verify DB version
        String dbVer = getDBVer();

        System.out.println("CurrentDBVer: '" + dbVer + "'");
        System.out.println("RequestDBVer: '" + apisrvDBVer + "'");

        // Check SAGA stuff? (how shoould I execute)
        // Do checks: (DB Ver, SAGA stuff, ...)
        if (dbVer.equals(apisrvDBVer)) {
            LOG.info("Current database version '" + dbVer
                    + "' is compatible with this code");
            LOG.info("Executing polling and controller threads ...");

            /*
             * Initialize the thread pool
             */
            asdExecutor = Executors.newFixedThreadPool(asdMaxThreads);

            /*
             * The first thread in the Pool is the polling thread
             */
            asdPolling = new APIServerDaemonPolling(asdExecutor);
            asdPolling.setConfig(asdConfig);
            asdExecutor.execute(asdPolling);

            /*
             * The second thread in the Pool is the controller thread
             */
            asdController = new APIServerDaemonController(asdExecutor);
            asdController.setConfig(asdConfig);
            asdExecutor.execute(asdController);
            LOG.info("Executed polling thread");
        } else {
            if (!dbVer.equals(apisrvDBVer)) {
                LOG.error("Current database version '" + dbVer
                         + "' is not compatible with requested version '"
                         + apisrvDBVer + "'");
            }

            LOG.error("The APIServerDaemon did not start!");
        }
    }

    /**
     * Retrieve the database version.
     *
     * @return Get current database schema version from DB
     */
    private String getDBVer() {
        APIServerDaemonDB asdDB = new APIServerDaemonDB(apisrvDBHost,
                                                        apisrvDBPort,
                                                        apisrvDBUser,
                                                        apisrvDBPass,
                                                        apisrvDBName);

        return asdDB.getDBVer();
    }
}
