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

import org.apache.log4j.Logger;

/**
 * Runnable class responsible to execute APIServerDaemon commands This class
 * does not handle directly APIServer API calls but rather uses
 * <target>Interface class instances The use of interface classes allow
 * targeting other command executor services.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 * @see GridEngineInterface
 */
class APIServerDaemonProcessCommand implements Runnable {

    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonProcessCommand.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");

    /**
     * Queue command record class.
     */
    private APIServerDaemonCommand asdCommand;
    /**
     * APIServerDaemon DB connection URL.
     */
    private String asdConnectionURL;

    /**
     * APIServerDaemon configuration class.
     */
    private APIServerDaemonConfig asdConfig;
    /**
     *  ProcessCommand thread name.
     */
    private String threadName;

    /**
     * Supported commands.
     */
    private enum Commands {
        /**
         * Release all task resources and any reference to it.
         */
        CLEAN,
        /**
         * Submit a task on a targeted infrastructure.
         */
        SUBMIT,
        /**
         * Get the status of a task (unused; no reference to API specs).
         */
        GETSTATUS,
        /**
         * Get the output of a task (unused; no reference to API specs).
         */
        GETOUTPUT,
        /**
         * Cancel the execution of a task (unused; no reference to API specs).
         */
        CANCEL,
        /**
         * Delete the reference of a task (unused; for fole based task DELETE).
         */
        DELETE,
        /**
         * Status change command.
         */
        STATUSCH
    }

    /**
     * Constructor that retrieves the command to execute and the
     * APIServerDaemon database connection URL, necessary to finalize executed
     * commands.
     *
     * @param command - The queue command record
     * @param connectionURL - The APIServerDaemon connection URL
     */
    APIServerDaemonProcessCommand(
            final APIServerDaemonCommand command,
            final String connectionURL) {
        this.asdCommand = command;
        this.asdConnectionURL = connectionURL;
        this.threadName = Thread.currentThread().getName();
    }

    /**
     * Execute a GridEngineDaemon 'cmdClean' command; just set PROCESSED so that
 the Controller can process it.
     */
    private void cmdClean() {
        LOG.debug("Clean command: " + asdCommand);

        if (asdCommand.getTarget().equals("GridEngine")) {
            GridEngineInterface geInterface =
                    new GridEngineInterface(asdCommand);

            asdCommand.setStatus("PROCESSED");
            asdCommand.update();
        } else {
            LOG.error("Unsupported target: '" + asdCommand.getTarget() + "'");
        }
    }

    /**
     * Execute a GridEngineDaemon 'job cancel' command.
     */
    private void cmdCancel() {
        LOG.debug("Job cancel command: " + asdCommand);

        if (asdCommand.getTarget().equals("GridEngine")) {
            GridEngineInterface geInterface =
                    new GridEngineInterface(asdCommand);

            geInterface.jobCancel();
            asdCommand.setStatus("PROCESSED");
            asdCommand.update();
        } else {
            LOG.error("Unsupported target: '" + asdCommand.getTarget() + "'");
        }
    }

    /**
     * Execute a cmdDelete command.
     */
    private void cmdDelete() {
        LOG.debug("Delete command: " + asdCommand);
        LOG.warn("Unsupported target: '" + asdCommand.getTarget() + "'");
    }

    /**
     * Execution of the APIServerCommand.
     */
    @Override
    public void run() {
        LOG.info("EXECUTING command: " + asdCommand);

        switch (Commands.valueOf(asdCommand.getAction())) {
        case CLEAN:
            cmdClean();
            break;

        case SUBMIT:
            cmdSubmit();
            break;

        case GETSTATUS:
            cmdGetStatus();
            break;

        case GETOUTPUT:
            cmdGetOutput();
            break;

        case CANCEL:
            cmdCancel();
            break;

        case DELETE:
            cmdDelete();
            break;

        case STATUSCH:
            cmdStatusChange();
            break;

        default:
            LOG.warn("Unsupported command: '" + asdCommand.getAction() + "'");
            // Set a final state for this command
            // todo ...
            break;
        }
    }

    /*
     * Commands implementations
     */

    /**
     * Execute a GridEngineDaemon 'cmdSubmit' command.
     */
    private void cmdSubmit() {
        LOG.debug("Submitting command: " + asdCommand
                + " - for target: '" + asdCommand.getTarget() + "'");

        switch (asdCommand.getTarget()) {
        case "GridEngine":
            GridEngineInterface geInterface =
                    new GridEngineInterface(asdConfig, asdCommand);
            int agiId = geInterface.jobSubmit(); // Currently this returns 0

            // agiId is taken from checkCommand loop
            asdCommand.setStatus("PROCESSED");
            asdCommand.update();
            LOG.debug("Submitted command (GridEngine): "
                    + asdCommand.toString());
            break;

        case "SimpleTosca":
            SimpleToscaInterface stInterface =
                    new SimpleToscaInterface(asdConfig, asdCommand);
            int simpleToscaId = stInterface.submitTosca();

            asdCommand.setTargetId(simpleToscaId);
            asdCommand.setStatus("PROCESSED");
            asdCommand.update();
            LOG.debug("Submitted command (SimpleTosca): "
                    + asdCommand.toString());
            break;

        case "ToscaIDC":
            ToscaIDCInterface tidcInterface =
                    new ToscaIDCInterface(asdConfig, asdCommand);
            int toscaIDCId = tidcInterface.submitTosca();

            asdCommand.setTargetId(toscaIDCId);
            asdCommand.setStatus("PROCESSED");
            asdCommand.update();
            LOG.debug("Submitted command (ToscaIDC): "
                    + asdCommand.toString());
            break;

        // case "<other_target>"
        // break;
        default:
            LOG.error("Unsupported target: '"
                    + asdCommand.getTarget() + "'");

            break;
        }
    }

    /**
     * Load APIServerDaemon configuration settings.
     *
     * @param config - APIServerDaemon configuration object
     */
    public void setConfig(final APIServerDaemonConfig config) {

        // Save all configs
        this.asdConfig = config;
    }

    /**
     * Execute a GridEngineDaemon 'get output' command Asynchronous GETOUTPUT
     * commands should never come here.
     */
    private void cmdGetOutput() {
        LOG.debug("Get output command: " + asdCommand);

        if (asdCommand.getTarget().equals("GridEngine")) {
            GridEngineInterface geInterface =
                    new GridEngineInterface(asdCommand);

            asdCommand.setTargetStatus(geInterface.jobOutput());
            asdCommand.setStatus("PROCESSED");
            asdCommand.update();
        } else {
            LOG.error("Unsupported target: '" + asdCommand.getTarget() + "'");
        }
    }

    /**
     * Execute a GridEngineDaemon 'status' command Asynchronous GETSTATUS
     * commands should never come here.
     */
    private void cmdGetStatus() {
        LOG.debug("Get status command: " + asdCommand);

        if (asdCommand.getTarget().equals("GridEngine")) {
            GridEngineInterface geInterface =
                    new GridEngineInterface(asdCommand);

            asdCommand.setTargetStatus(geInterface.jobStatus());
            asdCommand.setStatus("PROCESSED");
            asdCommand.update();
        } else {
            LOG.error("Unsupported target: '" + asdCommand.getTarget() + "'");
        }
    }

    /**
     * Execute a GridEngineDaemon 'cmdStatusChange' command.
     */
    private void cmdStatusChange() {
        LOG.debug("Status change for command: " + asdCommand
                + " - for target: '" + asdCommand.getTarget() + "'");

        switch (asdCommand.getTarget()) {
        case "GridEngine":
            LOG.warn("No status change handled for GridEngine EI");
            break;

        case "SimpleTosca":
            LOG.warn("No status change handled for SimpleTosca EI");
            break;

        case "ToscaIDC":
            ToscaIDCInterface tidcInterface =
                    new ToscaIDCInterface(asdConfig, asdCommand);
            int toscaIDCId = tidcInterface.submitTosca();

            if (asdCommand.getTargetStatus() == "CANCELLED") {
              String uuid = tidcInterface.getToscaUUID(asdCommand);
              tidcInterface.deleteToscaDeployment(uuid);
              // Get SUBMIT command and change its status to CANCELLED
              APIServerDaemonCommand subCmd = asdCommand.getSubmitCommand();
              subCmd.setStatus("CANCELLED");
              subCmd.update();
            }
            LOG.debug("Status changed for command (ToscaIDC): "
                    + asdCommand.toString());
            break;

        // case "<other_target>"
        // break;
        default:
            LOG.error("Unsupported target: '"
                    + asdCommand.getTarget() + "' for STATUSCH action");

            break;
        }
    }
}
