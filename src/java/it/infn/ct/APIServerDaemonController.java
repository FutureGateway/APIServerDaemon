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

import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.ExecutorService;

import org.apache.log4j.Logger;

/**
 * Runnable class that controls APIServerDaemon activities such as:
 * - Update job status values of any submitted task
 * - Manage job output request
 * - Preserve consistency status of any broken activity
 * - Cleanup done operations
 * This class implements one of the two principal APIServerDaemon threads
 * together with APIServerDaemonPolling class.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 * @see APIServerDaemonPolling
 */
public class APIServerDaemonController extends Observable implements Runnable {

    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonController.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");

    /*
     * APIServerDaemon Controller settings.
     */
    /**
     * APIServerDaemon controller status.
     */
    private boolean asControllerStatus = true;

    /**
     * APIServerDaemon Thread pool executor.
     */
    private ExecutorService asdExecutor = null;

    /*
     * APIServer API Server Database settings
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
     * APIServerDaemon database port.
     */
    private String apisrvDBPort;
    /**
     * APIServerDaemon database user.
     */
    private String apisrvDBUser;
    /**
     * APIServerDaemon database password.
     */
    private String apisrvDBPass;
    /**
     * APIServerDaemon controller delay name.
     */
    private int asControllerDelay;
    /**
     * APIServerDaemon controller max commands per loop.
     */
    private int asControllerMaxCommands;

    /*
     * APIServerDaemon config
     */

    /**
     * APIServerDaemon configuration class.
     */
    private APIServerDaemonConfig asdConfig;
    /**
     * APIServerDaemonController thread name.
     */
    private String threadName;

    /**
     * Instantiate a APIServerDaemonController allowing to execute further
     * threads using the given Executor object.
     *
     * @param executor
     *            Executor object created by the APIServerDaemon
     */
    public APIServerDaemonController(final ExecutorService executor) {
        this.asdExecutor = executor;
        threadName = Thread.currentThread().getName();
        LOG.info("Initializing APIServer PollingThread");
    }

    @Override
    public final void run() {
        APIServerDaemonDB asdDB = null;

        LOG.info("Starting APIServer ControllerThread");

        /**
         * APIServerDaemonController 'run' method loops until
         * geControllerStatus is true Polling loops takes only the following
         * kind of command statuses from the as_queue:
         * - PROCESSING: Verify time consistency retrying command if necessary
         * - SUBMITTED : as above
         * - RUNNING : as above
         * - DONE : Cleanup allocated space for expired tasks table and
         * then process them with the GrirEngineDaemonProcessCommand The same
         * kind of loop exists in the GridEngineDaemonPolling
         *
         * @see APIServerDaemonProcessCommand
         * @see APIServerDaemonPolling
         */
        while (asControllerStatus) {
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
                    asdDB.getControllerCommands(asControllerMaxCommands);

            LOG.debug("Received " + commands.size()
                    + "/" + asControllerMaxCommands
                    + " controller commands");

            /*
             * Process retrieved commands
             */
            Iterator<APIServerDaemonCommand> iterCmds = commands.iterator();

            while (iterCmds.hasNext()) {
                APIServerDaemonCommand asdCommand = iterCmds.next();
                APIServerDaemonCheckCommand asdCheckCmd =
                        new APIServerDaemonCheckCommand(asdCommand);

                if (asdCheckCmd != null) {
                asdCheckCmd.setConfig(asdConfig);
                asdExecutor.execute(asdCheckCmd);
                }
            }
            } catch (Exception e) {

            /* Do something */
            LOG.fatal("Unable to get APIServer commands");
            }

            /*
             * Wait for next loop
             */
            try {
            Thread.sleep(asControllerDelay);
            } catch (InterruptedException e) {
            asControllerStatus = false;
            }
        }
    }

    /**
     * Terminate the controller loop.
     */
    public final void terminate() {

        /*
         * Tells to the controller thread to exit from its loop
         */
        asControllerStatus = false;
        notifyObservers();
    }

    /**
     * Load APIServerDaemon configuration settings.
     *
     * @param config APIServerDaemon configuration object
     */
    public final void setConfig(final APIServerDaemonConfig config) {

        // Save configs
        this.asdConfig = config;

        // Set configuration values for this class
        this.apisrvDBHost = config.getApisrvDBHost();
        this.apisrvDBPort = config.getApisrvDBPort();
        this.apisrvDBUser = config.getApisrvDBUser();
        this.apisrvDBPass = config.getApisrvDBPass();
        this.apisrvDBName = config.getApisrvDBName();

        // Load APIServerDaemon settings
        this.asControllerDelay = config.getControllerDelay();
        this.asControllerMaxCommands = config.getControllerMaxCommands();
        LOG.info("APIServerDaemon config:" + LS
                + "  [Database]" + LS
                + "    db_host: '" + this.apisrvDBHost + "'" + LS
                + "    db_port: '" + this.apisrvDBPort + "'" + LS
                + "    db_user: '" + this.apisrvDBUser + "'" + LS
                + "    db_pass: '" + this.apisrvDBPass + "'" + LS
                + "    db_name: '" + this.apisrvDBName + "'" + LS
                + "  [Controller config]" + LS
                + "    ControllerDelay      : '"
                + this.asControllerDelay + "'" + LS
                + "    ControllerMaxCommands: '"
                + this.asControllerMaxCommands + "'" + LS);
    }
}
