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

//import java.io.BufferedWriter;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.OutputStreamWriter;
//import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

//import java.util.logging.Logger;
import org.apache.log4j.Logger;

/**
 * This is the Runnable class that implements the polling thread This class
 * implements one of the two principal GridEngineDaemon threads together with
 * APIServerDaemonPolling class.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 * @see GridEngineDaemonController
 */
class APIServerDaemonPolling implements Runnable {

    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonPolling.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");

    /*
     * GridEngineDaemon Polling settings
     */
    /**
     * Status of polling; it loops until it's true.
     */
    private boolean asPollingStatus = true;

    /**
     * Thread pool executor service.
     */
    private ExecutorService asdExecutor = null;

    /*
     * GridEngine API Server Database settings.
     */
    /**
     * APIServerDaemon database name.
     */
    private String apisrvDBName;
    /**
     * APIServerDaemon database host.
     */
    private String apisrvDBHost;
    /**
     * APIServerDaemon database port number.
     */
    private String apisrvDBPort;
    /**
     * APIServerDaemon database user name.
     */
    private String apisrvDBUser;
    /**
     * APIServerDaemon database password.
     */
    private String apisrvDBPass;
    /**
     * APIServerDaemon Polling delay time.
     */
    private int asPollingDelay;
    /**
     * APIServerDaemon maximun number of commands for polling loop.
     */
    private int asPollingMaxCommands;

    /*
     * GridEngineDaemon config
     */
    /**
     * APIServerDaemon configuration class.
     */
    private APIServerDaemonConfig asdConfig;
    /**
     * Polling thread name.
     */
    private String threadName;

    /**
     * Constructor receiving the threadpool executor object allowing this class
     * to submit further threads.
     *
     * @param executor
     *            The executor object instantiated from GridEngineDaemon class
     * @see GridEngineDaemon
     */
     APIServerDaemonPolling(final ExecutorService executor) {
        this.asdExecutor = executor;
        threadName = Thread.currentThread().getName();
        LOG.info("Initializing APIServer PollingThread");
    }

    /**
     * APIServerDaemonPolling 'run' method loops until asPollingStatus is true
     * Polling loops takes only WAITING status records from the as_queue table
     * and then process them with the APIServerDaemonProcessCommand The same
     * kind of loop exists in the APIServerDaemonController.
     *
     * @see APIServerDaemonProcessCommand
     * @see APIServerDaemonController
     */
    @Override
    public void run() {
        APIServerDaemonDB asdDB = null;

        LOG.info("Starting APIServer PollingThread");

        /*
         * PollingThread main loop; it gets available commands from queue
         */
        while (asPollingStatus) {
            try {

                /*
                 * Retrieves commands from DB
                 */
                asdDB = new APIServerDaemonDB(apisrvDBHost,
                                              apisrvDBPort,
                                              apisrvDBUser,
                                              apisrvDBPass,
                                              apisrvDBName);

                List<APIServerDaemonCommand> commands =
                        asdDB.getQueuedCommands(asPollingMaxCommands);

                LOG.debug("Received " + commands.size()
                        + "/" + asPollingMaxCommands
                        + " waiting commands");

                /*
                 * Process retrieved commands
                 */
                Iterator<APIServerDaemonCommand> iterCmds =
                        commands.iterator();

                while (iterCmds.hasNext()) {
                    APIServerDaemonCommand asdCommand = iterCmds.next();
                    APIServerDaemonProcessCommand asdProcCmd =
                            new APIServerDaemonProcessCommand(asdCommand,
                            asdDB.getConnectionURL());

                    if (asdProcCmd != null) {
                        asdProcCmd.setConfig(asdConfig);
                        asdExecutor.execute(asdProcCmd);
                    }
                }
            } catch (Exception e) {
                LOG.fatal("Unable to get APIServer commands");
            }

            /*
             * Wait for next loop
             */
            try {
                Thread.sleep(asPollingDelay);
            } catch (InterruptedException e) {
                asPollingStatus = false;
            }
        }
    }

    /**
     * Terminate the polling loop.
     */
    public void terminate() {

        /*
         * Tells to the polling thread to exit from its loop
         */
        asPollingStatus = false;
    }

    /**
     * Load APIServerDaemon configuration settings.
     *
     * @param config - APIServerDaemon configuration object
     */
    public void setConfig(final APIServerDaemonConfig config) {

        // Save configs
        this.asdConfig = config;

        // Set configuration values for this class
        this.apisrvDBHost = config.getApisrvDBHost();
        this.apisrvDBPort = config.getApisrvDBPort();
        this.apisrvDBUser = config.getApisrvDBUser();
        this.apisrvDBPass = config.getApisrvDBPass();
        this.apisrvDBName = config.getApisrvDBName();

        // Load GridEngineDaemon settings
        this.asPollingDelay = config.getPollingDelay();
        this.asPollingMaxCommands = config.getPollingMaxCommands();
        LOG.info("APIServerDaemon config:" + LS
                + "  [Database]" + LS
                + "    db_host: '" + this.apisrvDBHost + "'" + LS
                + "    db_port: '" + this.apisrvDBPort + "'" + LS
                + "    db_user: '" + this.apisrvDBUser + "'" + LS
                + "    db_pass: '" + this.apisrvDBPass + "'" + LS
                + "    db_name: '" + this.apisrvDBName + "'" + LS
                + "  [Polling config]" + LS
                + "    asPollingDelay  : '"
                + this.asPollingDelay + "'" + LS
                + "    asPollingMaxCommands: '"
                + this.asPollingMaxCommands + "'" + LS);
    }
}
