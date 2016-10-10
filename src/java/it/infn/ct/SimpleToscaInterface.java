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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.ogf.saga.context.Context;
import org.ogf.saga.context.ContextFactory;
import org.ogf.saga.error.IncorrectStateException;
import org.ogf.saga.error.NoSuccessException;
import org.ogf.saga.error.NotImplementedException;
import org.ogf.saga.error.PermissionDeniedException;
import org.ogf.saga.error.SagaException;
import org.ogf.saga.job.Job;
import org.ogf.saga.job.JobDescription;
import org.ogf.saga.job.JobFactory;
import org.ogf.saga.job.JobService;
import org.ogf.saga.session.Session;
import org.ogf.saga.session.SessionFactory;
import org.ogf.saga.task.State;
import org.ogf.saga.url.URL;
import org.ogf.saga.url.URLFactory;
import org.json.JSONObject;
import org.json.JSONArray;
import fr.in2p3.jsaga.impl.job.instance.JobImpl;
import fr.in2p3.jsaga.impl.job.service.JobServiceImpl;

/**
 * This class interfaces any call to the GridEngine library.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
public class SimpleToscaInterface {

    /*
     * Logger
     */
    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(SimpleToscaInterface.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");
    /**
     * File path separator.
     */
    public static final String FS = System.getProperty("file.separator");
    /**
     * JobOutput directory name.
     */
    public static final String JO = "jobOutput";

    /**
     * APIServerDaemon database connection URL.
     */
    private String apiServerConnURL = "";
    /**
     * APIServerDaemon queue command.
     */
    private APIServerDaemonCommand toscaCommand;
    /**
     * APIServerDaemon configuration class.
     */
    private APIServerDaemonConfig asdConfig;

    /**
     * Empty constructor for SimpleToscaInterface.
     */
    public SimpleToscaInterface() {
        LOG.debug("Initializing SimpleToscaInterface");
        System.setProperty("saga.factory",
                "fr.in2p3.jsaga.impl.SagaFactoryImpl");
    }

    /**
     * Constructor for SimpleTosca taking as input a given command.
     * @param command - Queue command
     */
    public SimpleToscaInterface(final APIServerDaemonCommand command) {
        this();
        LOG.debug("SimpleTosca command:" + LS + command);
        this.toscaCommand = command;
    }

    /**
     * Constructor for SimpleToscaInterface taking as input the
     * APIServerDaemonConfig and a given command.
     * @param config - APIServerDaemon configuration class
     * @param command - Queue command
     */
    public SimpleToscaInterface(final APIServerDaemonConfig config,
                                final APIServerDaemonCommand command) {
        this(command);
        setConfig(config);
    }

    /**
     * Read TOSCA resource information json file and stores data
     * in RuntimeData.
     */
    public final void saveResourceData() {
        String infoFilePath = getInfoFilePath();

        try {
            InputStream isInfo = new FileInputStream(infoFilePath);
            String jsonTxtInfo = IOUtils.toString(isInfo);
            JSONObject jsonResDesc = (JSONObject) new JSONObject(jsonTxtInfo);

            LOG.debug("Loaded resource information json:"
                    + LS + jsonResDesc);

            // "ip":"158.42.105.6"
            String infoIP = String.format("%s",
                    jsonResDesc.getString("ip"));

            LOG.debug("info_ip: '" + infoIP + "'");
            toscaCommand.setRunTimeData("simple_tosca_ip",
                    infoIP, "Resource IP address", "", "");

            // "port":22
            String infoSSHPort = String.format("%s",
                    "" + jsonResDesc.getInt("port"));

            LOG.debug("info_sshport: '" + infoSSHPort + "'");
            toscaCommand.setRunTimeData("simple_tosca_sshport",
                    infoSSHPort, "Resource ssh port address", "", "");

            // "username":"root"
            String infoSSHUsername = String.format("%s",
                    jsonResDesc.getString("username"));

            LOG.debug("info_sshusername: '" + infoSSHUsername + "'");
            toscaCommand.setRunTimeData("simple_tosca_sshusername",
                    infoSSHUsername, "Resource ssh username", "", "");

            // "password":"Vqpx3Hm4"
            String infoSSHPassword = String.format("%s",
                    jsonResDesc.getString("password"));

            LOG.debug("info_sshpassword: '" + infoSSHPassword + "'");
            toscaCommand.setRunTimeData("simple_tosca_sshpassword",
                    infoSSHPassword, "Resource ssh user password", "",
                    "");

            // "toscaId":"13da6aa0-d5a4-415b-ad8b-29ded3d4d006"
            String infoToscaId = String.format("%s",
                    jsonResDesc.getString("tosca_id"));

            LOG.debug("info_tosca_id: '" + infoToscaId + "'");
            toscaCommand.setRunTimeData("simple_tosca_id",
                    infoToscaId, "TOSCA resource UUID", "", "");

            // "job_id":
            //     "6f4d8d6c-c879-45aa-9d16-c82ca04688ce#\
            //      13da6aa0-d5a4-415b-ad8b-29ded3d4d006"
            String infoJobId = String.format("%s",
                    jsonResDesc.getString("job_id"));

            LOG.debug("info_job_id: '" + infoJobId + "'");
            toscaCommand.setRunTimeData("simple_tosca_jobid",
                    infoJobId, "JSAGA job identifier", "", "");
            LOG.debug("Successfully stored all resource data "
                    + "in RunTimeData for task: '" + toscaCommand.getTaskId()
                    + "'");
        } catch (FileNotFoundException ex) {
            LOG.error("File not found: '" + infoFilePath + "'");
        } catch (IOException ex) {
            LOG.error("I/O Exception reading file: '" + infoFilePath + "'");
        } catch (Exception ex) {
            LOG.error("Caught exception: '" + ex.toString() + "'");
        }
    }

    /**
     * Submit tosca job.
     * @param token - Access token (INDIGO AAI, other tokens ...
     * @param toscaEndPoint - Endpoint to TOSCA orchestrator
     * @param toscaTemplate - Path to yaml TOSCA template file
     * @param toscaParameters - Parameters for TOSCA template
     * @param executable - Executable to run in PaaS
     * @param output - Output file
     * @param error - Error file
     * @param args - Executable arguments
     * @param files - Path to files
     * @return Orchestrator job identifier UUID
     */
    public final  String submitJob(final String token,
                                   final String toscaEndPoint,
                                   final String toscaTemplate,
                                   final String toscaParameters,
                                   final String executable,
                                   final String output,
                                   final String error,
                                   final String[] args,
                                   final String[] files) {
        Session session = null;
        Context context = null;
        JobService service = null;
        Job job = null;
        String srvURL = "";
        String jobId = "";

        try {
            session = SessionFactory.createSession(false);
            context = ContextFactory.createContext("tosca");
            context.setAttribute("token", token);
            session.addContext(context);

            try {
                String infoFilePath = getInfoFilePath();

                LOG.info("Initialize the JobService context... ");
                srvURL = toscaEndPoint + "?" + "tosca_template="
                        + toscaTemplate + toscaParameters + "&info="
                        + infoFilePath;

                URL serviceURL = URLFactory.createURL(srvURL);

                LOG.info("Tosca ServiceURL = '" + serviceURL + "'");
                service = JobFactory.createJobService(session, serviceURL);

                JobDescription desc = JobFactory.createJobDescription();

                LOG.info("Setting up tosca attributes ...");
                LOG.debug("Executable: '" + executable + "'");
                desc.setAttribute(JobDescription.EXECUTABLE, executable);
                LOG.debug("Output: '" + output + "'");
                desc.setAttribute(JobDescription.OUTPUT, output);
                LOG.debug("Output: '" + error + "'");
                desc.setAttribute(JobDescription.ERROR, error);
                LOG.info("Setting up tosca verctor attributes ...");

                for (int i = 0; i < args.length; i++) {
                    LOG.debug("args[" + i + "]='" + args[i] + "'");
                }

                desc.setVectorAttribute(JobDescription.ARGUMENTS, args);

                for (int j = 0; j < files.length; j++) {
                    LOG.debug("files[" + j + "]='" + files[j] + "'");
                }

                desc.setVectorAttribute(desc.FILETRANSFER, files);
                LOG.info("Creating job ...");
                job = service.createJob(desc);
                LOG.info("Submit job ...");
                job.run();

                // Getting the jobId
                jobId = job.getAttribute(Job.JOBID);
                LOG.info("Job instance created with jobId: '" + jobId + "'");

                try {
                    ((JobServiceImpl) service).disconnect();
                } catch (NoSuccessException ex) {
                    LOG.error("See below the stack trace... ");
                    ex.printStackTrace(System.out);
                }

                LOG.info("Closing session...");
                session.close();
            } catch (Exception ex) {
                LOG.error("Failed to initialize the JobService");
                LOG.error("See below the stack trace... ");
                ex.printStackTrace(System.out);
            }
        } catch (Exception ex) {
            LOG.error("Failed to initialize the security context"
                    + LS + "See below the stack trace... ");
            ex.printStackTrace(System.out);
        }

        return jobId;
    }

    /**
     * Process JSON object containing information stored in file:
     * <action_info>/<task_id>.json and submit using tosca adaptor.
     * @return TOSCA orchestrator qUUID
     */
    public final int submitTosca() {
        int simpleToscaId = 0;
        JSONObject jsonJobDesc = null;
        LOG.debug("Entering submitSimpleTosca");
        String jobDescFileName = toscaCommand.getActionInfo()
                + FS + toscaCommand.getTaskId() + ".json";
        LOG.debug("JSON filename: '" + jobDescFileName + "'");
        try {
            // Prepare jobOutput dir for output sandbox
            String outputSandbox = toscaCommand.getActionInfo() + FS + JO;
            LOG.debug("Creating job output directory: '"
                    + outputSandbox + "'");
            File outputSandboxDir = new File(outputSandbox);
            if (!outputSandboxDir.exists()) {
                LOG.debug("Creating job output directory");
                outputSandboxDir.mkdir();
                LOG.debug("Job output successfully created");
            } else {
                // Directory altready exists; clean all its content
                LOG.debug("Cleaning job output directory");
                FileUtils.cleanDirectory(outputSandboxDir);
                LOG.debug("Successfully cleaned job output directory");
            }
            // Now read values from JSON and prepare the submission accordingly
            InputStream is = new FileInputStream(jobDescFileName);
            String jsonTxt = IOUtils.toString(is);
            jsonJobDesc = (JSONObject) new JSONObject(jsonTxt);
            LOG.debug("Loaded APIServer JobDesc:\n" + LS + jsonJobDesc);
            // Username (unused yet but later used for accounting)
            String user = String.format("%s", jsonJobDesc.getString("user"));
            LOG.debug("User: '" + user + "'");
            // Get app Info and Parameters
            JSONObject appInfo = new JSONObject();
            appInfo = jsonJobDesc.getJSONObject("application");
            JSONArray appParams = new JSONArray();
            appParams = appInfo.getJSONArray("parameters");
            // Application parameters
            String executable = "";
            String output = "";
            String error = "";
            String arguments = "";
            for (int i = 0; i < appParams.length(); i++) {
                JSONObject appParameter = appParams.getJSONObject(i);
                // Get parameter name and value
                String paramName = appParameter.getString("param_name");
                String paramValue = appParameter.getString("param_value");
                switch (paramName) {
                case "target_executor":
                    LOG.debug("target_executor: '" + paramValue + "'");
                    break;
                case "jobdesc_executable":
                    executable = paramValue;
                    LOG.debug("executable: '" + executable + "'");
                    break;
                case "jobdesc_output":
                    output = paramValue;
                    LOG.debug("output: '" + output + "'");
                    break;
                case "jobdesc_error":
                    error = paramValue;
                    LOG.debug("error: '" + error + "'");
                    break;
                case "jobdesc_arguments":
                    arguments = paramValue;
                    LOG.debug("arguments: '" + arguments + "'");
                    break;
                default:
                    LOG.warn("Unsupported application parameter name: '"
                            + paramName + "' with value: '" + paramValue
                            + "'");
                }
            }
            // Arguments
            String jobArgs = arguments;
            JSONArray jobArguments = jsonJobDesc.getJSONArray("arguments");
            for (int j = 0; j < jobArguments.length(); j++) {
                jobArgs += ((jobArgs.length() > 0) ? "," : "")
                        + jobArguments.getString(j);
            }
            String[] args = jobArgs.split(",");
            for (int k = 0; k < args.length; k++) {
                LOG.debug("args[" + k + "]: '" + args[k] + "'");
            }
            // Infrastructures
            // Select one of the possible infrastructures among the enabled
            // ones. A random strategy is currently implemented; this could be
            //changed later
            JSONArray jobInfrastructures =
                    appInfo.getJSONArray("infrastructures");
            JSONArray enabledInfras = new JSONArray();
            for (int v = 0, w = 0; w < jobInfrastructures.length(); w++) {
                JSONObject infra = jobInfrastructures.getJSONObject(w);
                if (infra.getString("status").equals("enabled")) {
                    enabledInfras.put(v++, infra);
                }
            }
            int selInfraIdx = 0;
            Random rndGen = new Random();
            if (enabledInfras.length() > 1) {
                selInfraIdx = rndGen.nextInt(enabledInfras.length());
            }
            JSONObject selInfra = new JSONObject();
            selInfra = enabledInfras.getJSONObject(selInfraIdx);
            LOG.debug("Selected infra: '" + LS + selInfra.toString() + "'");
            // Infrastructure parameters
            String toscaEndPoint = "";
            String toscaParameters = "";
            String toscaTemplate = "";
            String token = "";
            JSONArray infraParams = selInfra.getJSONArray("parameters");
            for (int h = 0; h < infraParams.length(); h++) {
                JSONObject infraParameter = infraParams.getJSONObject(h);
                String paramName = infraParameter.getString("name");
                String paramValue = infraParameter.getString("value");
                switch (paramName) {
                case "tosca_endpoint":
                    toscaEndPoint = paramValue;
                    LOG.debug("tosca_endpoint: '" + toscaEndPoint + "'");
                    break;
                case "tosca_token":
                    token = paramValue;
                    LOG.debug("tosca_token: '" + token + "'");
                    break;
                case "tosca_template":
                    toscaTemplate = toscaCommand.getActionInfo()
                            + "/" + paramValue;
                    LOG.debug("tosca_template: '" + toscaTemplate + "'");
                    break;
                case "tosca_parameters":
                    toscaParameters = "&" + paramValue;
                    LOG.debug("tosca_parameters: '" + toscaParameters + "'");
                    break;
                default:
                    LOG.warn("Unsupported infrastructure parameter name: '"
                            + paramName + "' with value: '"
                            + paramValue + "'");
                }
            }
            // Prepare JSAGA IO file list
            String ioFiles = "";
            JSONArray inputFiles = jsonJobDesc.getJSONArray("input_files");
            for (int i = 0; i < inputFiles.length(); i++) {
                JSONObject fileEntry = inputFiles.getJSONObject(i);
                String fileName = fileEntry.getString("name");
                ioFiles += ((ioFiles.length() > 0) ? "," : "")
                        + toscaCommand.getActionInfo() + FS
                        + fileEntry.getString("name") + ">"
                        + fileEntry.getString("name");
            }
            JSONArray outputFiles = jsonJobDesc.getJSONArray("output_files");
            for (int j = 0; j < outputFiles.length(); j++) {
                JSONObject fileEntry = outputFiles.getJSONObject(j);
                String fileName = fileEntry.getString("name");
                ioFiles += ((ioFiles.length() > 0) ? "," : "")
                        + toscaCommand.getActionInfo() + FS + JO + FS
                        + fileEntry.getString("name") + "<"
                        + fileEntry.getString("name");
            }
            LOG.debug("IOFiles: '" + ioFiles + "'");
            String[] files = ioFiles.split(",");
            for (int i = 0; i < files.length; i++) {
                LOG.debug("IO Files[" + i + "]: '" + files[i] + "'");
            }
            // Add info file name to toscaParameters
            String infoFile = ((toscaParameters.length() > 0) ? "&" : "?")
                    + "info=" + toscaCommand.getActionInfo() + FS
                    + toscaCommand.getTaskId() + "_simpleTosca.json";
            // Finally submit the job
            String toscaId = submitJob(token,
                                       toscaEndPoint,
                                       toscaTemplate,
                                       toscaParameters,
                                       executable,
                                       output,
                                       error,
                                       args,
                                       files);
            LOG.info("tosca_id: '" + toscaId + "'");
            // Register JobId, if targetId exists it is a submission retry
            SimpleToscaInterfaceDB stiDB = null;
            String submitStatus = "SUBMITTED";
            try {
                stiDB = new SimpleToscaInterfaceDB(apiServerConnURL);
                int toscaTargetId = toscaCommand.getTargetId();
                if (toscaTargetId > 0) {
                    // update toscaId if successful
                    if ((toscaId != null) && (toscaId.length() > 0)) {
                        stiDB.updateToscaId(toscaTargetId, toscaId);
                        // Save job related info in runtime_data
                        saveResourceData();
                    } else {
                        submitStatus = "ABORTED";
                    }
                    toscaCommand.setTargetStatus(submitStatus);
                    stiDB.updateToscaStatus(toscaTargetId, submitStatus);
                    LOG.debug("Updated existing entry in simple_tosca "
                            + "table at id: '" + toscaTargetId + "'"
                            + "' - status: '" + submitStatus + "'");
                } else {
                    LOG.debug("Creating a new entry in simple_tosca table "
                            + "for submission: '" + toscaId + "'");
                    if (toscaId.length() == 0) {
                        submitStatus = "ABORTED";
                    }
                    toscaCommand.setTargetStatus(submitStatus);
                    simpleToscaId = stiDB.registerToscaId(toscaCommand,
                                                            toscaId,
                                                            submitStatus);
                    // Save job related info in runtime_data
                    saveResourceData();
                    LOG.debug("Registered in simple_tosca with id: '"
                            + simpleToscaId + "' - status: '"
                            + submitStatus + "'");
                }
            } catch (Exception e) {
                LOG.fatal("Unable to register tosca_id: '" + toscaId + "'");
            }
        } catch (SecurityException se) {
            LOG.error("Unable to create job output folder in: '"
                    + toscaCommand.getActionInfo() + "' directory");
        } catch (Exception ex) {
            LOG.error("Caught exception: '" + ex.toString() + "'");
        }
        return simpleToscaId;
    }

    /**
     * Load APIServerDaemon configuration settings.
     *
     * @param config - APIServerDaemon configuration object
     */
    public final void setConfig(final APIServerDaemonConfig config) {
        this.asdConfig = config;
        this.apiServerConnURL = config.getApisrvURL();
    }

    /**
     * Return the SimpleTosca resource information file path.
     * @return Info file path
     */
    public final String getInfoFilePath() {
        return toscaCommand.getActionInfo() + FS
             + toscaCommand.getTaskId() + "_SimpleTosca.json";
    }

    /**
     * GetNativeJobId.
     * @param jobId - Retrieve native job identifier
     * @return Native job identifier
     */
    private static String getNativeJobId(final String jobId) {
        String nativeJobId = "";
        Pattern pattern = Pattern.compile("\\[(.*)\\]-\\[(.*)\\]");
        Matcher matcher = pattern.matcher(jobId);

        try {
            if (matcher.find()) {
                nativeJobId = matcher.group(2);
            } else {
                return null;
            }
        } catch (Exception ex) {
            System.out.println(ex.toString());

            return null;
        }

        return nativeJobId;
    }

    /**
     * This method returns the job output dir used for this interface.
     * @return Job output dir name
     */
    public static String getOutputDir() {
        return JO;
    }

    /**
     * Get the status of a simpleTosca execution.
     * @return Status of simpleTosca submission
     */
    public final String getStatus() {
        LOG.debug("getStatus (begin)");
        Session session = null;
        Context context = null;
        JobService service = null;
        Job job = null;
        String srvURL = "";
        String jobId = "";
        String status = "";
        // toscaId comes from simple_tosca database table through
        // toscaCommand.task_id field
        String toscaId = getToscaId();
        if ((toscaId != null) && (toscaId.length() > 0)) {
            try {
                LOG.debug("Creating context and session");
                session = SessionFactory.createSession(false);
                context = ContextFactory.createContext("tosca");
                context.setAttribute("token",
                                     "AABBCCDDEEFF00112233445566778899");
                session.addContext(context);
                LOG.debug("Getting status for toscaId: '" + toscaId + "'");
                srvURL = toscaId.substring(1, toscaId.indexOf("?"));
                URL serviceURL = URLFactory.createURL(srvURL);
                LOG.debug("serviceURL = '" + serviceURL + "'");
                service = JobFactory.createJobService(session, serviceURL);
                String nativeJobId = getNativeJobId(toscaId);
                job = service.getJob(nativeJobId);
                State state = null;
                try {
                    LOG.debug("Fetching the status of the job: '"
                            + toscaId + "'");
                    LOG.debug("nativeJobId: '" + nativeJobId + "'");
                    state = job.getState();
                    status = state.name();
                    LOG.debug("Current Status = '" + status + "'");
                    // String executionHosts[];
                    // executionHosts =
                    // job.getVectorAttribute(Job.EXECUTIONHOSTS);
                    // LOG.debug("Execution Host = " + executionHosts[0]);
                    // Perform the right action related to its status
                    if (State.CANCELED.compareTo(state) == 0) {
                        LOG.info("");
                        LOG.info("Job Status == CANCELED ");
                    } else if (State.FAILED.compareTo(state) == 0) {
                        LOG.info("Job Status == FAILED");
                        LOG.debug("getting EXITCODE");
                        try {
                            String exitCode = job.getAttribute(Job.EXITCODE);
                            LOG.info("Exit Code (" + exitCode + ")");
                        } catch (SagaException ex) {
                            LOG.error("Unable to get exit code");
                            LOG.debug(ex.toString());
                        } finally {
                            // Release the resource
                            try {
                                job.cancel();
                                LOG.debug("Job cancelled successfully");
                            } catch (NoSuccessException ex) {
                                LOG.debug("Service disconnected "
                                        + "unsuccessfully");
                                LOG.error("See below the stack trace... ");
                                LOG.error(ex.toString());
                            } finally {
                                try {
                                    ((JobServiceImpl) service).disconnect();
                                    LOG.debug("Service disconnected "
                                            + "successfully");
                                } catch (NoSuccessException ex) {
                                    LOG.debug("Service disconnected "
                                            + "unsuccessfully");
                                    LOG.error("See below the stack trace... ");
                                    LOG.error(ex.toString());
                                }
                            }
                        }
                    } else if (State.DONE.compareTo(state) == 0) {
                        LOG.debug("Job Status == DONE");
                        LOG.debug("getting exit code");
                        try {
                            String exitCode = job.getAttribute(Job.EXITCODE);
                            LOG.debug("Exit code: '" + exitCode + "'");
                            // postStaging and cleanup
                            try {
                                LOG.debug("Post staging and cleanup");
                                ((JobImpl) job).postStagingAndCleanup();
                                LOG.info("Job outputs successfully "
                                        + "retrieved");
                            } catch (NotImplementedException ex) {
                                LOG.error(ex.toString());
                            } catch (PermissionDeniedException ex) {
                                LOG.error(ex.toString());
                            } catch (IncorrectStateException ex) {
                                LOG.error(ex.toString());
                            } catch (NoSuccessException ex) {
                                LOG.error(ex.toString());
                            }
                            // disconnect
                            try {
                                ((JobServiceImpl) service).disconnect();
                                LOG.debug("Service disconnected "
                                        + "successfully");
                            } catch (NoSuccessException ex) {
                                LOG.debug("Service disconnected "
                                        + "unsuccessfully");
                                LOG.error("See below the stack trace... ");
                                LOG.error(ex.toString());
                            }
                        } catch (Exception ex) {
                            LOG.error("Unable to get exit code");
                        }
                    } else {
                        LOG.error("Unhandled status '" + state.name() + "'");
                    }
                } catch (Exception ex) {
                    LOG.error("Error in getting job status");
                    LOG.error(ex.toString());
                    LOG.error("Cause : '" + ex.getCause() + "'");
                }
            } catch (Exception ex) {
                // Context problem
                LOG.error("Unable to create context");
            } finally {
                session.close();
                LOG.debug("Session closed");
                // Now update simple_tosca
                SimpleToscaInterfaceDB stiDB = null;
                try {
                    stiDB = new SimpleToscaInterfaceDB(apiServerConnURL);
                    stiDB.updateToscaStatus(toscaCommand.getTargetId(),
                                            status);
                } catch (Exception ex) {
                }
            }
        } else {
            LOG.debug("Unable to get tosca_id");
        }
        LOG.info("getStatus (end)");
        return status;
    }
    /**
     * Retrieve the TOSCA orchestrator UUID.
     * @return TOSCA Orchestrator UUID
     */
    public final String getToscaId() {
        String toscaId = "";
        SimpleToscaInterfaceDB stiDB = null;
        try {
            stiDB = new SimpleToscaInterfaceDB(apiServerConnURL);
            LOG.debug("GetToscaId for task_id: '"
                    + toscaCommand.getTaskId() + "'");
            toscaId = stiDB.getToscaId(toscaCommand);
        } catch (Exception e) {
            LOG.error("Unable to get tosca_id for task_id: '"
                    + toscaCommand.getTaskId() + "'");
        }
        LOG.debug("tosca_id: '" + toscaId + "'");
        return toscaId;
    }
}
