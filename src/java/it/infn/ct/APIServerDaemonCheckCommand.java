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

//import java.lang.Runtime;
import org.apache.log4j.Logger;

/**
 * Runnable class responsible to check APIServerDaemon commands This class
 * mainly checks for commands consistency and it does not handle directly
 * APIServer API calls but rather uses APIServerInterface class instances The
 * use of an interface class may help targeting other command executor services
 * if needed.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 * @see GridEngineInterface
 */
public class APIServerDaemonCheckCommand implements Runnable {

    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonCheckCommand.class.getName());

    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");

    /**
     * File path separator constant.
     */
    public static final String FS = System.getProperty("file.separator");

    /**
     * APIServerDaemon queue command record.
     */
    private APIServerDaemonCommand asdCommand;

    /**
     * APIServerDaemon configuration class.
     */
    private APIServerDaemonConfig asdConfig;

    /**
     * APIServerDaemon Check command thread name.
     */
    private String threadName;

    /**
     * Queue entries commands supported by executor interfaces.
     */
    private enum Commands {
        /**
         * This command cleans any task entry and file system allocation.
         */
        CLEAN,
        /**
         * Triggers the executor for a submission action.
         */
        SUBMIT,
        /**
         * Get status action(*).
         * (*) This command is directly handled by G&C Eng Executor interface
         */
        GETSTATUS,
        /**
         * Get output action(*).
         * (*) This command is directly handled by G&C Eng Executor interface
         */
        GETOUTPUT,
        /**
         * Terminate an executing task.
         */
        CANCEL,
    }

    /**
     * Constructor that retrieves the command to execute and the APIServerDaemon
     * database connection URL, necessary to finalize executed commands.
     *
     * @param asdCmd APIServerDaemon queue command
     */
    public APIServerDaemonCheckCommand(
            final APIServerDaemonCommand asdCmd) {
        asdCommand = asdCmd;
        threadName = Thread.currentThread().getName();
    }

    /**
     * Clean everything associated to the task I/O Sandbox and any
     * DB allocation.
     */
    private void clean() {

        // Remove any API Server task entries including the queue
        removeTaksEntries();

        // Remove info directory
        removeInfoDir();
    }

    /**
     * Execute a APIServerDaemon 'job cancel' command.
     */
    private void cancel() {
        LOG.debug("Check job cancel command: " + asdCommand);
    }

    /**
     * Remove actionInfo directory from the file system.
     */
    private void removeInfoDir() {
        String infoDir = asdCommand.getActionInfo();

        try {
            Process delInfoDir =
                    Runtime.getRuntime().exec("rm -rf " + infoDir);

            delInfoDir.waitFor();
        } catch (Exception e) {
            LOG.fatal("Error removing infoDIR: '" + infoDir + "'");
        }
    }

    /**
     * Remove any task entry from the DB including the queue record.
     */
    private void removeTaksEntries() {
        APIServerDaemonDB asdDB = null;

        LOG.debug("Removing task: '" + asdCommand.getTaskId() + "'");

        // Now remove task entries in APIServer DB
        try {
            // First take care to remove specific target entries
            switch (asdCommand.getTarget()) {
                case "GridEngine":
                    // First prepare the GridEngineInterface passing config
                    GridEngineInterface geInterface =
                            new GridEngineInterface(asdConfig, asdCommand);

                    // Retrieve the right agi_id field exploiting the
                    // fixed jobDescription field inside the
                    // ActiveGridInteraction agiId may change during
                    // submission in casethe job is resubmitted by
                    //the GridEngine
                    int agiId = geInterface.getAGIId();

                    LOG.debug("AGIId for command having id:"
                            + asdCommand.getTaskId()
                            + " is: " + agiId);
                    asdCommand.setTargetId(agiId);
                    asdCommand.update();

                    // Now verify if target exists
                    if (asdCommand.getTargetId() > 0) {
                        LOG.debug("Removing record for GridEngine: '"
                                + asdCommand.getTaskId()
                                + "' -> AGI: '"
                                + asdCommand.getTargetId() + "'");

                        // Task should be in RUNNING state
                        if (asdCommand.getStatus().equals("RUNNING")) {
                        LOG.warn("Removing a GridEngine' RUNNING task,"
                               + " its job execution will be lost");
                        }

                        geInterface.removeAGIRecord(geInterface.getAGIId());
                    } else {
                        LOG.debug("No GridEngine ActiveGridInteraction record "
                                + "is asssociated to the task: '"
                                + asdCommand.getTaskId() + "'");
                    }
                break;

                case "SimpleTosca":
                    LOG.warn("Delete taks for SimpleTosca "
                            + "not yet implemented!");
                break;

                case "ToscaIDC":
                    LOG.warn("Delete taks for ToscaIDC "
                            + "not yet implemented!");
                break;

                // Place other targets below ...
                // case "mytarget":
                // break;
                default:
                    LOG.warn("Unrecognized target '" + asdCommand.getTarget()
                        + "' while deleting target specific task entries");
            }

                // Now remove APIServer DB task entries
                asdDB = new APIServerDaemonDB(
                        asdCommand.getASDConnectionURL());
                asdDB.removeTaksEntries(asdCommand.getTaskId());
        } catch (Exception e) {
            LOG.fatal("Unable to remove task entries for command:" + LS
                    + asdCommand + LS
                    + e.toString());
        }
    }

    /**
     * Check of the APIServerCommand
     *
     * Checking depends by the couple (action,status) Statuses taken by
     * CheckCommand are: PROCESSING: The command is being processed
     *                   PROCESSED: The command has been processed
     *
     * Action    | PROCESSING  | PROCESSED       | Target
     * ----------+-------------+-----------------+-----------
     * Submit    | Consistency | Check job status|    (*)
     * ----------+-------------+-----------------+-----------
     * GetStatus |      -      |       -         |
     * ----------+-------------+-----------------+-----------
     * GetOutput |      -      |       -         |
     * ----------+-------------+-----------------+-----------
     * JobCancel | Consistency | Check on GE     |
     * ----------+-------------+-----------------+-----------
     *
     * (*) GridEngine,JSAGA,pySAGA,EUDAT, ...
     *     Any target may have different Action/Status values
     *
     * GetStatus and GetOutput are synchronous operations directly handled by
     * the APIServer engine for this reason these actions are not supported
     * directly
     * Consistency check verifies how long the command waits in order to be
     * processed, if it takes too long the command could be re-queued and/or
     * tagged as FAILED.
     *
     * Check job status verifies the job status inside the specific target
     * For isntance in the GridEngine' case it will be checked the
     * ActiveGridInteraction table. This will verify that job has been
     * cancelled on the GridEngine as well
     * Same mechanisms can be applied to other interfaces
     *
     */
    @Override
    public final void run() {
    LOG.debug("Checking command: " + asdCommand);

    switch (Commands.valueOf(asdCommand.getAction())) {
    case CLEAN:
        clean();

        break;

    case SUBMIT:
        submit();

        break;

    case GETSTATUS:
        getStatus();

        break;

    case GETOUTPUT:
        getOutput();

        break;

    case CANCEL:
        cancel();

        break;

    default:
        LOG.warn("Unsupported command: '" + asdCommand.getAction() + "'");

        break;
    }
    }

    /*
     * Commands implementations
     */

    /**
     * Check a APIServerDaemon 'submit' command.
     */
    private void submit() {
        LOG.debug("Checking submitted command: " + asdCommand);

        // Add a check for long lasting PROCESSING commands
        switch (asdCommand.getStatus()) {

        // PROCESSING - The command have been taken from the task
        //              queue and provided to its target executor
        case "PROCESSING":
            checkProcessing();
            break;

        // PROCESSED - The command has been processed by the target executor
        case "PROCESSED":
            checkProcessed();
            break;

        default:
            LOG.error("Ignoring unsupported status: '"
                    + asdCommand.getStatus() + "' for task: "
                    + asdCommand.getTaskId());
        } // switch on STATUS

        // Updating check_ts field a round-robing strategy will be
        // applied while extracting command from the queue by controller
        asdCommand.checkUpdate();
    }

    /**
     * Check the consistency of the given command.
     * @param mode - Consistency check mode: PROCESSING, PROCESSED
     */
    private void taskConsistencyCheck(final String mode) {

        // This check consistency of the command execution
        // if it takes too long the command should be
        // resubmitted or flagged as FAILED reaching a given
        // threshold
        // Tasks will be retryed if creation and last change is
        // greater than max_wait and retries have not reached yet
        // the max_retry count
        // Trashed requests will be flagged as FAILED
        LOG.debug("Consistency of '" + mode
                + "' task - id: " + asdCommand.getTaskId()
                + " lifetime: " + asdCommand.getLifetime()
                + "/" + asdConfig.getTaskMaxWait()
                + " - retry: " + asdCommand.getRetry()
                + "/" + asdConfig.getTaskMaxRetries());

        if ((asdCommand.getRetry() < asdConfig.getTaskMaxRetries())
            && (asdCommand.getLifetime() > asdConfig.getTaskMaxWait())) {
            LOG.debug("Retrying PROCESSED task having id: "
                    + asdCommand.getTaskId());
            asdCommand.retry();
        } else if (asdCommand.getRetry() >= asdConfig.getTaskMaxRetries()) {
            LOG.debug("Trashing PROCESSED task having id: "
                    + asdCommand.getTaskId());
            asdCommand.trash();
        } else {
            LOG.debug("Ignoring at the moment '"
                    + mode + "' task having id: "
                    + asdCommand.getTaskId());
        }
    }

    /**
     * update task' output file paths.
     * @param outputDir - The path associated to the given command
     */
    final void updateOutputPaths(final String outputDir) {
        APIServerDaemonDB asdDB = null;

        try {
            asdDB = new APIServerDaemonDB(asdCommand.getASDConnectionURL());
            asdDB.updateOutputPaths(asdCommand, outputDir);
        } catch (Exception e) {

            // LOG.severe("Unable release command:"+LS+asdCommand
            LOG.fatal("Unable release command:" + LS
                    + asdCommand + LS
                    + e.toString());
        }
    }

    /**
     * Load APIServerDaemon configuration settings.
     *
     * @param asdCfg APIServerDaemon configuration object
     */
    public final void setConfig(final APIServerDaemonConfig asdCfg) {
        // Save all configs
        asdConfig = asdCfg;
    }

    /**
     * Execute a APIServerDaemon 'get output' command Asynchronous GETOUTPUT
     * commands should never come here.
     */
    private void getOutput() {
    LOG.debug("Check get output command: " + asdCommand);
    }

    /**
     * Execute a APIServerDaemon 'status' command Asynchronous GETSTATUS
     * commands should never come here.
     */
    private void getStatus() {
    LOG.debug("Checkinig get status command: " + asdCommand);
    }

    /**
     * Check PROCESSING state queue commands.
     */
    private void checkProcessing() {
        // Verify how long the task remains in PROCESSING state
        // if longer than MaxWait, retry the command if the number
        // of retries did not reached yet the MaxRetries value
        // otherwise trash the task request (marked as FAILED)
        // Provide a different behavior depending on the Target
        if (asdCommand.getTarget().equals("GridEngine")) {
            taskConsistencyCheck("PROCESSING");
        } else if (asdCommand.getTarget().equals("SimpleTosca")) {
            taskConsistencyCheck("PROCESSING");
        } else if (asdCommand.getTarget().equals("ToscaIDC")) {
            taskConsistencyCheck("PROCESSING");
        } else {
            LOG.warn("Unsupported target: '" + asdCommand.getTarget() + "'");
        }
    }

    /**
     * Check PROCESSED queue commands.
     */
    private void checkProcessed() {
        // Verify that TargetId exists, if yes check the status
        // otherwise check task consistency
        // Provide a different behavior depending on the Target
        if (asdCommand.getTarget().equals("GridEngine")) {
        // Status is PROCESSED; the job has been submited
        // First prepare the GridEngineInterface passing config
        GridEngineInterface geInterface =
                new GridEngineInterface(asdConfig, asdCommand);
        // Retrieve the right agi_id field exploiting the
        // fixed jobDescription field inside the ActiveGridInteraction
        // agiId may change during submission in casethe job is
        // resubmitted by the GridEngine
        int agiId = geInterface.getAGIId();
        LOG.debug("AGIId for command having id:"
                + asdCommand.getTaskId()
                + " is: " + agiId);
        asdCommand.setTargetId(agiId);
        asdCommand.update();
        // update target_status taking its value from the GridEngine'
        // ActiveGridInteraction table, then if target_status is DONE
        // flag also the command state to DONE allowing APIServer'
        // GetOutput call to work
        if (asdCommand.getTargetId() != 0) {
            String geJobStatus = geInterface.jobStatus();
            LOG.debug("Status of job " + asdCommand.getTaskId()
                    + " is '" + geJobStatus + "'");
            asdCommand.setTargetStatus(geJobStatus);
            if ((asdCommand.getTargetStatus() != null)
             && (asdCommand.getTargetStatus().length() > 0)) {
                switch (asdCommand.getTargetStatus()) {
                    case "DONE":
                        asdCommand.setStatus("DONE");
                        // DONE command means that jobOutput is ready
                        String outputDir = geInterface.prepareJobOutput();
                        updateOutputPaths(outputDir);
                        break;
                    case "RUNNING":
                        // as_queue status field must remain 'PROCESSED'
                        // asdCommand.setStatus("RUNNING");
                        break;
                    default:
                        LOG.warn("Unhandled status: '" + geJobStatus + "'");
                        break;
                    }
                }
                asdCommand.update();
            } else {
                // TargetId is 0 - check consistency ...
                taskConsistencyCheck("PROCESSED");
            }
        } else if (asdCommand.getTarget().equals("SimpleTosca")) {
            // Determine the status and take care of the output files
            SimpleToscaInterface stInterface =
                    new SimpleToscaInterface(asdConfig, asdCommand);
            String currState = asdCommand.getStatus(); // Register the
                                   // current status
                                   // (PROCESSED)
            asdCommand.setStatus("HOLD"); // Avoid during check that futher
                                          // checks occur
            asdCommand.update();
            String status = stInterface.getStatus();
            if ((status != null) && (status.length() > 0)) {
                asdCommand.setTargetStatus(status);
                switch (status) {
                    case "DONE":
                        currState = status;
                        updateOutputPaths(SimpleToscaInterface.getOutputDir());
                    break;
                    case "RUNNING":
                        // as_queue status field must remain 'PROCESSED'
                        // currState = status;
                    break;
                    default:
                        asdCommand.setTargetStatus(status);
                        LOG.warn("Unhandled status: '" + status + "'");
                }
            } else {
                LOG.warn("No status available yet");
                // No status is available - check consistency ...
                taskConsistencyCheck("PROCESSED");
                if (!asdCommand.getStatus().equals("HOLD")) {
                currState = asdCommand.getStatus();
                }
            }
            asdCommand.setStatus(currState); // Setup the current state
            asdCommand.update();
        } else if (asdCommand.getTarget().equals("ToscaIDC")) {
            // Determine the status and take care of the output files
            ToscaIDCInterface tidcInterface =
                    new ToscaIDCInterface(asdConfig, asdCommand);
            String currState = asdCommand.getStatus(); // Register the
                                   // current status
                                   // (PROCESSED)
            asdCommand.setStatus("HOLD"); // Avoid during check that futher
                              // checks occur
            asdCommand.update();
            String status = tidcInterface.getStatus();
            if ((status != null) && (status.length() > 0)) {
                asdCommand.setTargetStatus(status);
                switch (status) {
                    case "DONE":
                        currState = status;
                        updateOutputPaths(ToscaIDCInterface.getOutputDir());
                    break;
                    case "RUNNING":
                        // as_queue status field must remain 'PROCESSED'
                        // currState = status;
                    break;
                    /* Check statuses are coming from infrastructure this is
                       wrong; cancel requests must come from a separate
                       as_queue record
                    case "CANCELLED":
                        String uuid = tidcInterface.getToscaUUID(asdCommand);
                        tidcInterface.deleteToscaDeployment(uuid);
                        currState = "CANCELLED";
                    break;
                    */
                    default:
                        asdCommand.setTargetStatus(status);
                        LOG.warn("Unhandled status: '" + status + "'");
                    }
            } else {
                LOG.warn("No status available yet");
                // No status is available - check consistency ...
                taskConsistencyCheck("PROCESSED");
                if (!asdCommand.getStatus().equals("HOLD")) {
                currState = asdCommand.getStatus();
                }
            }
            asdCommand.setStatus(currState); // Setup the current state
            asdCommand.update();
        } else {
            LOG.warn("Unsupported target: '" + asdCommand.getTarget() + "'");
        }
    }
}
