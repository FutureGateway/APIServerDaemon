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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * APIServerDaemon interface for TOSCA.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
public class ToscaIDCInterface {
    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonController.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");
    /**
     * File patch separator.
     */
    public static final String FS = System.getProperty("file.separator");
    /**
     * Job output dir name.
     */
    public static final String JO = "jobOutput";
    /**
     * HTTP code 200.
     */
    public static final int HTTP_200 = 200;
    /**
     * HTTP code 201.
     */
    public static final int HTTP_201 = 201;
    /**
     * HTTP code 404.
     */
    public static final int HTTP_204 = 201;

    /**
     * APIServerDaemon database connection URL.
     */
    private String apiServerConnURL = "";
    /**
     * Queue command.
     */
    private APIServerDaemonCommand toscaCommand;
    /**
     * APIServerDaemon configuration class.
     */
    private APIServerDaemonConfig asdConfig;

    /*
     * Tosca parameters
     */
    /**
     * TOSCA orchestrator endpoint.
     */
    private URL toscaEndPoint = null;
    /**
     * AAI Token to access orchestrator.
     */
    private String toscaToken = "";
    /**
     * File path containing the yaml TOSCA template.
     */
    private String toscaTemplate = "";
    /**
     * TOSCA parameters.
     */
    private String toscaParameters = "";
    /**
     * TOSCA informative file.
     */
    private String informtativeFile = "";
    /**
     * TOSCA orchestrator UUID.
     */
    private String toscaUUID = "";
    /**
     * ToscaIDC DB interface class.
     */
    private ToscaIDCInterfaceDB tiiDB = null;

    /**
     * Empty constructor for ToscaIDCInterface.
     */
    public ToscaIDCInterface() {
        LOG.debug("Initializing ToscaIDCInterface");
        System.setProperty("saga.factory",
                "fr.in2p3.jsaga.impl.SagaFactoryImpl");
    }

    /**
     * Constructor for ToscaIDCInterface taking as input a given command.
     * @param command - Queue command
     */
    public ToscaIDCInterface(final APIServerDaemonCommand command) {
        this();
        LOG.debug("ToscaIDC command:" + LS + command);
        this.toscaCommand = command;
    }

    /**
     * Constructor for ToscaIDCInterface taking as input the
     * APIServerDaemonConfig and a given command.
     * @param config - Configuration file
     * @param command - Queue command
     */
    public ToscaIDCInterface(final APIServerDaemonConfig config,
                             final APIServerDaemonCommand command) {
        this(command);
        setConfig(config);
        tiiDB = new ToscaIDCInterfaceDB(apiServerConnURL);
        if (tiiDB == null) {
            LOG.error("Unable to instantiate ToscaIDCInterface DB class");
        } else {
            LOG.debug("Syccessfully instantiate ToscaIDCInterface DB class");
        }
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
     * Return the ToscaIDCInterface resource information file path.
     * @return Command file information path
     */
    public final String getInfoFilePath() {
        return toscaCommand.getActionInfo()
             + FS + toscaCommand.getTaskId() + "_toscaIDC.json";
    }

    /**
     * process JSON object containing information stored in file:
     * <action_info>/<task_id>.json and submit using tosca adaptor.
     *
     * @return TOSCA UUID number
     */
    public final int submitTosca() {
        int toscaId = 0;
        org.json.JSONObject jsonJobDesc = null;

        LOG.debug("Entering sumitTosca (ToscaIDC)");

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

            jsonJobDesc =
                    (org.json.JSONObject) new org.json.JSONObject(jsonTxt);
            LOG.debug("Loaded APIServer JobDesc:\n" + LS + jsonJobDesc);

            // Username (unused yet but later used for accounting)
            String user = String.format("%s", jsonJobDesc.getString("user"));
            LOG.debug("User: '" + user + "'");

            // Get app Info and Parameters
            org.json.JSONObject appInfo = new org.json.JSONObject();
            appInfo = jsonJobDesc.getJSONObject("application");
            JSONArray appParams = new JSONArray();
            appParams = appInfo.getJSONArray("parameters");

            // Application parameters
            String executable = "";
            String output = "";
            String error = "";
            String arguments = "";

            for (int i = 0; i < appParams.length(); i++) {
                org.json.JSONObject appParameter = appParams.getJSONObject(i);

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
            // ones a random strategy is currently implemented; this could be
            // changed later
            JSONArray jobInfrastructures =
                    appInfo.getJSONArray("infrastructures");
            JSONArray enabledInfras = new JSONArray();

            for (int v = 0, w = 0; w < jobInfrastructures.length(); w++) {
                org.json.JSONObject infra =
                        jobInfrastructures.getJSONObject(w);

                if (infra.getString("status").equals("enabled")) {
                    enabledInfras.put(v++, infra);
                }
            }

            int selInfraIdx = 0;
            Random rndGen = new Random();

            if (enabledInfras.length() > 1) {
                selInfraIdx = rndGen.nextInt(enabledInfras.length());
            }

            org.json.JSONObject selInfra = new org.json.JSONObject();

            selInfra = enabledInfras.getJSONObject(selInfraIdx);
            LOG.debug("Selected infra: '" + LS + selInfra.toString() + "'");

            // Infrastructure parameters
            JSONArray infraParams = selInfra.getJSONArray("parameters");

            for (int h = 0; h < infraParams.length(); h++) {
                org.json.JSONObject infraParameter =
                        infraParams.getJSONObject(h);
                String paramName = infraParameter.getString("name");
                String paramValue = infraParameter.getString("value");

                switch (paramName) {
                case "tosca_endpoint":
                    toscaEndPoint = new URL(paramValue);
                    toscaCommand.setRunTimeData("tosca_endpoint",
                            toscaEndPoint.toString(),
                            "TOSCA endpoint", "", "");
                    LOG.debug("tosca_endpoint: '" + toscaEndPoint + "'");

                    break;

                case "tosca_template":
                    toscaTemplate = toscaCommand.getActionInfo()
                            + "/" + paramValue;
                    LOG.debug("tosca_template: '" + toscaTemplate + "'");

                    break;

                case "tosca_parameters":
                    toscaParameters +=
                            ((toscaParameters.length() > 0) ? "&" : "")
                            + paramValue;
                    LOG.debug("tosca_parameters: '" + toscaParameters + "'");

                    break;

                default:
                    LOG.warn("Ignoring infrastructure parameter name: '"
                            + paramName
                            + "' with value: '" + paramValue
                            + "'");
                }
            }

            // Prepare JSAGA IO file list
            String ioFiles = "";
            JSONArray inputFiles = jsonJobDesc.getJSONArray("input_files");

            for (int i = 0; i < inputFiles.length(); i++) {
                org.json.JSONObject fileEntry = inputFiles.getJSONObject(i);
                String fileName = fileEntry.getString("name");

                ioFiles += ((ioFiles.length() > 0) ? "," : "")
                        + toscaCommand.getActionInfo() + FS
                        + fileEntry.getString("name") + ">"
                        + fileEntry.getString("name");
            }

            JSONArray outputFiles = jsonJobDesc.getJSONArray("output_files");

            for (int j = 0; j < outputFiles.length(); j++) {
                org.json.JSONObject fileEntry = outputFiles.getJSONObject(j);
                String fileName = fileEntry.getString("name");

                ioFiles += ((ioFiles.length() > 0) ? "," : "")
                        + toscaCommand.getActionInfo()
                        + FS + JO + FS
                        + fileEntry.getString("name")
                        + "<" + fileEntry.getString("name");
            }

            LOG.debug("IOFiles: '" + ioFiles + "'");

            String[] files = ioFiles.split(",");

            for (int i = 0; i < files.length; i++) {
                LOG.debug("IO Files[" + i + "]: '" + files[i] + "'");
            }

            // Add info file name to tParams
            toscaParameters += ((toscaParameters.length() > 0) ? "&" : "")
                    + "info=" + toscaCommand.getActionInfo() + FS
                    + toscaCommand.getTaskId() + "_toscaIDC.json";
            LOG.debug("TOSCA parameters: '" + toscaParameters + "'");

            toscaToken = tiiDB.getToken(toscaCommand);
            LOG.debug("Token for toscaCommand having id: '"
                    + toscaCommand.getTaskId()
                    + "' is: '" + toscaToken + "'");

            // Finally submit the job
            toscaUUID = submitOrchestrator();
            LOG.info("toscaUUID: '" + toscaUUID + "'");

            // Register JobId, if targetId exists it is a submission retry
            try {
                String submitStatus = "SUBMITTED";
                int toscaTargetId = toscaCommand.getTargetId();

                if (toscaTargetId > 0) {

                    // update tosca_id if successful
                    if ((toscaUUID != null) && (toscaUUID.length() > 0)) {
                        tiiDB.updateToscaId(toscaTargetId, toscaUUID);
                    } else {
                        submitStatus = "ABORTED";
                    }

                    toscaCommand.setTargetStatus(submitStatus);
                    tiiDB.updateToscaStatus(toscaTargetId, submitStatus);
                    LOG.debug("Updated existing entry in simple_tosca "
                            + "(ToscaIDC) table at id: '" + toscaTargetId + "'"
                            + "' - status: '" + submitStatus + "'");
                } else {
                    LOG.debug("Creating a new entry in simple_tosca "
                            + "table for submission: '" + toscaUUID + "'");

                    if (toscaUUID.length() == 0) {
                        submitStatus = "ABORTED";
                    }

                    toscaCommand.setTargetStatus(submitStatus);
                    toscaId = tiiDB.registerToscaId(toscaCommand,
                            toscaUUID, toscaEndPoint.toString(),
                            submitStatus);

                    LOG.debug("Registered in simple_tosca "
                            + "(ToscaIDC) with id: '"
                            + toscaId + "' - status: '"
                            + submitStatus + "'");
                }
            } catch (Exception e) {
                LOG.fatal("Unable to register tosca_id: '" + toscaUUID + "'");
            } finally {
                toscaCommand.update();
            }
        } catch (SecurityException se) {
            LOG.error("Unable to create job output folder in: '"
                    + toscaCommand.getActionInfo() + "' directory");
        } catch (Exception ex) {
            LOG.error("Caught exception: '" + ex.toString() + "'");
        }

        return toscaId;
    }

    /**
     * Submit template to the orchestrator a template.
     * @return TOSCA UUID
     */
    public final String submitOrchestrator() {

        StringBuilder orchestratorResult = new StringBuilder("");
        StringBuilder postData = new StringBuilder();
        String toscParametersValues = "";
        String toscaParametersJson = "";
        String tUUID = "";
        String[] toscaParams = toscaParameters.split("&");
        String tParams = "";
        for (int i = 0; i < toscaParams.length; i++) {
            String[] paramArgs = toscaParams[i].split("=");
            if (paramArgs[0].trim().equals("params")) {
                toscaParametersJson = toscaCommand.getActionInfo()
                        + FS + paramArgs[1].trim();
                LOG.debug("Loading params json file: '"
                        + toscaParametersJson + "'");
                try {
                    String paramsJson =
                            new String(Files.readAllBytes(
                                    Paths.get(toscaParametersJson)));
                    LOG.debug("params JSON: '" + paramsJson + "'");
                    toscParametersValues =
                            getDocumentValue(paramsJson, "parameters");
                    LOG.debug("extracted parameters: '"
                            + tParams + "'");
                } catch (IOException ex) {
                    LOG.error("Parameters json file '"
                            + toscaParametersJson + "' is not readable");
                    LOG.error(ex);
                } catch (ParseException ex) {
                    LOG.error("Parameters json file '"
                            + toscaParametersJson + "' is not parseable");
                    LOG.error(ex);
                }
                LOG.debug("Parameters json file '"
                        + toscaParametersJson + "' successfully parsed");
                break;
            }
        }
        if (toscParametersValues.length() > 0) {
            tParams = "\"parameters\": " + toscParametersValues + ", ";
        }
        postData.append("{ " + tParams + "\"template\": \"");
        String toscaTemplateContent = "";
        LOG.debug("Escaping toscaTemplate file '" + toscaTemplate + "'");
        try {
            toscaTemplateContent =
                    new String(Files.readAllBytes(
                            Paths.get(toscaTemplate))).replace("\n", "\\n");
        } catch (IOException ex) {
            LOG.error("Template '" + toscaTemplate + "'is not readable");
            LOG.error(ex);
        }
        postData.append(toscaTemplateContent);
        postData.append("\" }");
        LOG.debug("JSON Data (begin):\n" + postData + "\nJSON Data (end)");

        HttpURLConnection conn;
        String orchestratorDoc = "";
        try {
            conn = (HttpURLConnection) toscaEndPoint.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + toscaToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setDoOutput(true);
            OutputStreamWriter wr =
                    new OutputStreamWriter(conn.getOutputStream());
            wr.write(postData.toString());
            wr.flush();
            wr.close();
            LOG.debug("Orchestrator status code: " + conn.getResponseCode());
            LOG.debug("Orchestrator status message: "
                    + conn.getResponseMessage());
            if (conn.getResponseCode() == HTTP_201) {
                BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(conn.getInputStream()));
                orchestratorResult = new StringBuilder();
                String ln;
                while ((ln = br.readLine()) != null) {
                    orchestratorResult.append(ln);
                }
                LOG.debug("Orchestrator result: " + orchestratorResult);
                orchestratorDoc = orchestratorResult.toString();
                tUUID = getDocumentValue(orchestratorDoc, "uuid");
                LOG.debug("Created resource has UUID: '" + tUUID + "'");
            } else {
                LOG.warn("Orchestrator return code is: "
                        + conn.getResponseCode());
            }
        } catch (IOException ex) {
            LOG.error("Connection error with the service at "
                    + toscaEndPoint.toString());
            LOG.error(ex);
        } catch (ParseException ex) {
            LOG.error("Error parsing JSON:" + orchestratorDoc);
            LOG.error(ex);
        }
        return tUUID;
    }

    /**
     * Read values from the json.
     *
     * @param json -The json from where to
     * @param key - The element to return. It can retrieve nested elements
     *              providing the full chain as
     *              &lt;element&gt;.&lt;element&gt;.&lt;element&gt;
     * @return The element value
     * @throws ParseException If the json cannot be parsed
     */
    protected final String getDocumentValue(final String json,
                                            final  String key)
            throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(json);
        String[] keyelement = key.split("\\.");
        for (int i = 0; i < (keyelement.length - 1); i++) {
            jsonObject = (JSONObject) jsonObject.get(keyelement[i]);
        }
        return jsonObject.get(keyelement[keyelement.length - 1]).toString();
    }

    /**
     * This method returns the job output dir used for this interface.
     * @return Job output dir
     */
    public static String getOutputDir() {
        return JO;
    }

    /**
     * Return deployment information of a given tUUID.
     *
     * @param uuid - TOSCA UUID
     * @param token - Orchestrator access token
     * @return Orchestrator deployment information
     */
    protected final String getToscaDeployment(final String uuid,
                                              final String token) {
        StringBuilder deployment = new StringBuilder();
        HttpURLConnection conn;
        URL deploymentEndpoint = null;
        try {
            LOG.debug("endpoint: '" + toscaEndPoint + "'");
            deploymentEndpoint =
                    new URL(toscaEndPoint.toString() + "/" + uuid);
            LOG.debug("deploymentEndpoint: '" + deploymentEndpoint + "'");
            conn = (HttpURLConnection) deploymentEndpoint.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            LOG.debug("Orchestrator status code: " + conn.getResponseCode());
            LOG.debug("Orchestrator status message: "
                    + conn.getResponseMessage());
            if (conn.getResponseCode() == HTTP_200) {
                BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(conn.getInputStream()));
                String ln;
                while ((ln = br.readLine()) != null) {
                    deployment.append(ln);
                }
                LOG.debug("Orchestrator result: " + deployment);
            }
        } catch (IOException ex) {
            LOG.error("Connection error with the service at "
                    + deploymentEndpoint.toString());
            LOG.error(ex);
        }
        return deployment.toString();
    }

    /**
     * GetStatus of TOSCA submission.
     *
     * @return Status o TOSCA UUID
     */
    public final String getStatus() {
        LOG.debug("Entering IDC getStatus ...");
        String status = toscaCommand.getTargetStatus();
        try {
            // toscaEndPoint = new
            // URL(toscaCommand.getRunTimeData("tosca_endpoint"));
            toscaEndPoint = new URL(tiiDB.toscaEndPoint(toscaCommand));
        } catch (MalformedURLException ex) {
            LOG.error("Unable to get endpoint from command: '"
                    + toscaCommand + "'");
        }
        LOG.debug("tosca endpoint: '" + toscaEndPoint + "'");
        String tUUID = tiiDB.getToscaId(toscaCommand);
        LOG.debug("tosca UUID: '" + tUUID + "'");
        String tToken = tiiDB.getToken(toscaCommand);
        LOG.debug("tosca Token: '" + tToken + "'");
        String toscaDeploymentInfo = getToscaDeployment(tUUID, tToken);
        LOG.debug("tosca deployment info: '" + toscaDeploymentInfo + "'");
        try {
            status = getDocumentValue(toscaDeploymentInfo, "status");
        } catch (ParseException ex) {
            LOG.error("Unable to parse deployment result: '"
                    + toscaDeploymentInfo + "'");
        }
        // Do status mapping (orchestrator->JSAGA style)
        // Check for DONE status; this saves the informative file
        if (status.equals("CREATE_COMPLETE")) {
            status = "DONE";
            // When deployment is done save in runtime data outputs field
            informtativeFile = getInfoFilePath();
            try {
                String outputs =
                        getDocumentValue(toscaDeploymentInfo, "outputs");
                LOG.debug("Output for deployment having UUID: '"
                        + tUUID + "' is: '" + outputs + "'");
                saveInformativeFile(outputs);
                toscaCommand.setRunTimeData("tosca_outputs",
                                            informtativeFile,
                                            "TOSCA deployiment outputs field",
                                            "file://", "plain/text");
                LOG.debug("Successfully generated informativeFile at: '"
                        + informtativeFile
                        + "' and registered it on runtime data");
            } catch (Exception ex) {
                LOG.error("Unable to parse deployment info: '"
                        + toscaDeploymentInfo + "' looking for outputs field");
            }
        } else if (status.equals("CREATE_FAILED")) {
            status = "ABORT";
        } else if (status.equals("CREATE_IN_PROGRESS")) {
            status = "RUNNING";
        } else {
            // status = "UNKNOWN";
            LOG.error("Unhespected ToscaIDC status: '" + status + "'");
        }
        LOG.debug("Status of deployment having id: '"
                + tUUID + "' is: '" + status + "'");
        // update target status
        tiiDB.updateToscaStatus(toscaCommand.getTargetId(), status);
        LOG.debug("Leaving IDC getStatus");
        return status;
    }

    /**
     * Delete deployment having the given UUID.
     *
     * @param uuid - Tosca orchestrator UUID
     */
    protected final void deleteToscaDeployment(final String uuid) {
        StringBuilder deployment = new StringBuilder();
        HttpURLConnection conn;
        try {
            URL deploymentEndpoint =
                    new URL(toscaEndPoint.toString() + "/" + uuid);
            conn = (HttpURLConnection) deploymentEndpoint.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bearer " + toscaToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            LOG.debug("Orchestrator status code: "
                    + conn.getResponseCode());
            LOG.debug("Orchestrator status message: "
                    + conn.getResponseMessage());
            if (conn.getResponseCode() == HTTP_204) {
                LOG.debug("Successfully removed resource: '"
                        + uuid + "'");
            } else {
                LOG.error("Unable to remove resource: '"
                        + uuid + "'");
            }
        } catch (IOException ex) {
            LOG.error("Connection error with the service at "
                    + toscaEndPoint.toString());
            LOG.error(ex);
        }
    }

    /**
     * Save informative file.
     *
     * @param infoData - Deployment informative data to save
     */
    protected final void saveInformativeFile(final String infoData) {
        Writer writer = null;

        try {
            writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(informtativeFile), "utf-8"));
            writer.write(infoData);
            LOG.debug("Saved info file: '" + informtativeFile
                    + "' data: '" + infoData + "'");
        } catch (IOException ex) {
            LOG.error("Unable to save info file: '" + informtativeFile
                    + "' data: '" + infoData + "'");
        }
    }

}
