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

import it.infn.ct.GridEngine.Job.InfrastructureInfo;
import it.infn.ct.GridEngine.Job.JSagaJobSubmission;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.Random;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import it.infn.ct.GridEngine.Job.MultiInfrastructureJobSubmission;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class interfaces any call to the GridEngine library.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
public class GridEngineInterface {

    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(GridEngineInterface.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");
    /**
     * FridEngine ApplicationId associated to the APIServer executions.
     */
    public static final int GRIDENGINE_APISRVAPPID = 10000;
    /**
     * 0xFF bitmask.
     */
    public static final short FF_BITMASK = 0xff;
    /*
     * GridEngine UsersTracking DB
     */
    /**
     * UsersTrackingDB JNDI resource name.
     */
    private String utdbJNDI;
    /**
     * UsersTrackingDB host name.
     */
    private String utdbHost;
    /**
     * UsersTrackingDB host port number.
     */
    private String utdbPort;
    /**
     * UsersTrackingDB host user name.
     */
    private String utdbUser;
    /**
     * UsersTrackingDB host password.
     */
    private String utdbPass;
    /**
     * UsersTrackingDB host database name.
     */
    private String utdbName;

    /**
     * GridEngineDaemon configuration class.
     */
    private APIServerDaemonConfig gedConfig;

    /**
     * GridEngineDaemon IP address.
     */
    private String gedIPAddress;
    /**
     * The queue command.
     */
    private APIServerDaemonCommand gedCommand;

    /**
     * Empty constructor for GridEngineInterface.
     */
    public GridEngineInterface() {
        LOG.debug("Initializing GridEngineInterface");

        // Retrieve host IP address, used by JobSubmission
        getIP();

        // Prepare environment variable for GridEngineLogConfig.xml
        setupGELogConfig();
    }

    /**
     * Constructor for GridEngineInterface taking as input a given command.
     * @param command - The queue comand
     */
    public GridEngineInterface(final APIServerDaemonCommand command) {
        this();
        LOG.debug("GridEngineInterface command:" + LS + command);
        this.gedCommand = command;
    }

    /**
     * Constructor for GridEngineInterface taking as input the
     * APIServerDaemonConfig and a given command.
     * @param config - APIServerDaemon configuration class
     * @param command - Queue command
     */
    public GridEngineInterface(final APIServerDaemonConfig config,
                               final APIServerDaemonCommand command) {
        this();
        LOG.debug("GridEngineInterface command:" + LS + command);
        setConfig(config);
        this.gedCommand = command;
    }

    /**
     * submit the job identified by the gedCommand values.
     */
    public final void jobCancel() {
        LOG.debug("Cancelling job");

        return;
    }

    /**
     * submit the job identified by the gedCommand values.
     * @return path to output (not implemented)
     */
    public final String jobOutput() {
        LOG.debug("Getting job output");

        return "NOTIMPLEMENTED";
    }

    /**
     * submit the job identified by the gedCommand values.
     * @return Job status
     */
    public final String jobStatus() {
        String jobStatus = null;
        GridEngineInterfaceDB geiDB = null;

        LOG.debug("Getting job status");

        // It is more convenient to directly query the ActiveGridInteraction
        // since GridEngine JobCheck threads are in charge to update this
        try {
            geiDB = new GridEngineInterfaceDB(utdbHost,
                                              utdbPort,
                                              utdbUser,
                                              utdbPass,
                                              utdbName);
            jobStatus = geiDB.getJobStatus(gedCommand.getTargetId());
        } catch (Exception e) {
            LOG.fatal("Unable get command status:" + LS
                    + gedCommand + LS
                    + e.toString());
        }

        return jobStatus;
    }

    /**
     * Return a JSON object containing information stored in file:
     * <action_info>/<task_id>.json file, which contains the job description
     * built by the APIServer translated for the GridEngine.
     * @return JSON object with the GridEngine job descripton
     * @throws IOException Exception in case of IO failures
     */
    private JSONObject mkGEJobDesc() throws IOException {
        JSONObject jsonJobDesc = null;

        LOG.debug("Entering mkGEJobDesc");

        String jobDescFileName = gedCommand.getActionInfo()
                + "/" + gedCommand.getTaskId() + ".json";

        LOG.debug("JSON filename: " + jobDescFileName);

        try {
            InputStream is = new FileInputStream(jobDescFileName);
            String jsonTxt = IOUtils.toString(is);

            jsonJobDesc = (JSONObject) new JSONObject(jsonTxt);
            LOG.debug("Loaded APIServer JobDesc:\n" + LS + jsonJobDesc);
        } catch (Exception e) {
            LOG.warn("Caught exception: " + e.toString());
        }

        // Now create the <task_id>.info file targeted for the GridEngine
        JSONObject geTaskDescription = new JSONObject();

        geTaskDescription.put("commonName",
                String.format("%s", jsonJobDesc.getString("user")));
        geTaskDescription.put("application",
                GRIDENGINE_APISRVAPPID); // Take this value from properties
                                         // or any other configuration source
        geTaskDescription.put("identifier",
                String.format("%s@%s", jsonJobDesc.getString("id"),
                        jsonJobDesc.getString("iosandbox")));
        geTaskDescription.put("input_files",
                jsonJobDesc.getJSONArray("input_files"));
        geTaskDescription.put("output_files",
                jsonJobDesc.getJSONArray("output_files"));

        // Prepare the JobDescription
        JSONObject geJobDescription = new JSONObject();

        // Get app Info and Parameters
        JSONObject appInfo = new JSONObject();
        appInfo = jsonJobDesc.getJSONObject("application");
        JSONArray appParams = new JSONArray();
        appParams = appInfo.getJSONArray("parameters");

        // Process application parameters
        String jobArgs = "";
        String paramName;
        String paramValue;

        for (int i = 0; i < appParams.length(); i++) {
            JSONObject appParameter = appParams.getJSONObject(i);

            // Get parameter name and value
            paramName = appParameter.getString("param_name");
            paramValue = appParameter.getString("param_value");

            // Map task values to GE job description values
            if (paramName.equals("jobdesc_executable")) {
                geJobDescription.put("executable", paramValue);
            } else if (paramName.equals("jobdesc_arguments")) {

                // Further arguments will be added later
                jobArgs = paramValue + " ";
            } else if (paramName.equals("jobdesc_output")) {
                geJobDescription.put("output", paramValue);
            } else if (paramName.equals("jobdesc_error")) {
                geJobDescription.put("error", paramValue);
            } else if (paramName.equals("target_executor")) {
                LOG.debug("target_executor : '" + paramValue + "'");
            } else {
                LOG.warn("Reached end of if-elif chain for "
                        + "application param name: '"
                        + paramName + "' with value: '"
                        + paramValue + "'");
            }
        }

        // Now add further arguments if specified in task
        JSONArray jobArguments = jsonJobDesc.getJSONArray("arguments");

        for (int j = 0; j < jobArguments.length(); j++) {
            jobArgs += String.format("%s ", jobArguments.getString(j));
        }

        geJobDescription.put("arguments", jobArgs.trim());

        // Get application specific settings
        geTaskDescription.put("jobDescription", geJobDescription);

        // Select one of the possible infrastructures among the one enabled
        // A random strategy is currently implemented; this could be changed
        // later
        JSONArray jobInfrastructures = appInfo.getJSONArray("infrastructures");
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
        LOG.debug("Selected infra:" + LS + selInfra.toString());

        // Process infrastructure: name, credentials and parameters
        JSONObject geInfrastructure = new JSONObject();

        geInfrastructure.put("name", selInfra.getString("name"));

        JSONObject geCredentials = new JSONObject();
        JSONArray infraParams = selInfra.getJSONArray("parameters");

        for (int h = 0; h < infraParams.length(); h++) {
            JSONObject infraParameter = infraParams.getJSONObject(h);

            paramName = infraParameter.getString("name");
            paramValue = infraParameter.getString("value");
            LOG.info(h + ": " + paramName + " - " + paramValue);

            // Job settings
            if (paramName.equals("jobservice")) {
                geInfrastructure.put("resourceManagers", paramValue);
            } else if (paramName.equals("ce_list")) {
                geInfrastructure.put("ce_list", paramValue);
            } else if (paramName.equals("os_tpl")) {
                geInfrastructure.put("os_tpl", paramValue);
            } else if (paramName.equals("resource_tpl")) {
                geInfrastructure.put("resource_tpl", paramValue);
            } else if (paramName.equals("secured")) {
                geInfrastructure.put("secured", paramValue);
            } else if (paramName.equals("protocol")) {
                geInfrastructure.put("protocol", paramValue);
            } else if (paramName.equals("attributes_title")) {
                geInfrastructure.put("attributes_title", paramValue);
            } else if (paramName.equals("bdii")) {
                geInfrastructure.put("bdii", paramValue);
            } else if (paramName.equals("swtags")) {
                geInfrastructure.put("swtags", paramValue);
            } else if (paramName.equals("jdlRequirements")) {
                geInfrastructure.put("jdlRequirements", paramValue);
            } else if (paramName.equals("user_data")) {
                geInfrastructure.put("user_data", paramValue);
            } else if (paramName.equals("prefix")) {
                geInfrastructure.put("prefix", paramValue);
            } else if (paramName.equals("link")) {
                geInfrastructure.put("link", paramValue);
            } else if (paramName.equals("waitms")) {
                geInfrastructure.put("waitms", paramValue);
            } else if (paramName.equals("waitsshms")) {
                geInfrastructure.put("waitsshms", paramValue);
            } else if (paramName.equals("sshport")) {
                geInfrastructure.put("sshport", paramValue);

                // Credential settings
            } else if (paramName.equals("username")) {
                geCredentials.put("username", paramValue);
            } else if (paramName.equals("password")) {
                geCredentials.put("password", paramValue);
            } else if (paramName.equals("eToken_host")) {
                geCredentials.put("eToken_host", paramValue);
            } else if (paramName.equals("eToken_port")) {
                geCredentials.put("eToken_port", paramValue);
            } else if (paramName.equals("eToken_id")) {
                geCredentials.put("eToken_id", paramValue);
            } else if (paramName.equals("voms")) {
                geCredentials.put("voms", paramValue);
            } else if (paramName.equals("voms_role")) {
                geCredentials.put("voms_role", paramValue);
            } else if (paramName.equals("rfc_proxy")) {
                geCredentials.put("rfc_proxy", paramValue);
            } else if (paramName.equals("disable-voms-proxy")) {
                geCredentials.put("disable-voms-proxy", paramValue);
            } else if (paramName.equals("proxy-renewal")) {
                geCredentials.put("proxy-renewal", paramValue);
            } else {
                LOG.warn("Reached end of if-elif chain for infra_param name: '"
                        + paramName + "' with value: '"
                        + paramValue + "'");
            }
        }

        geTaskDescription.put("infrastructure", geInfrastructure);
        geTaskDescription.put("credentials", geCredentials);

        // Now write the JSON translated for the GridEngine
        String jsonTask = geTaskDescription.toString();
        String jsonFileName = gedCommand.getActionInfo()
                + "/" + gedCommand.getTaskId() + ".ge_info";

        try {
            OutputStream os = new FileOutputStream(jsonFileName);

            os.write(jsonTask.getBytes(
                    Charset.forName("UTF-8"))); // UTF-8 from properties
            LOG.debug("GridEngine JobDescription written in file '"
                    + jsonFileName + "':\n" + LS + jsonTask);
        } catch (Exception e) {
            LOG.warn("Caught exception: " + e.toString());
        }

        return geTaskDescription;
    }

    /**
     * Prepare the I/O Sandbox.
     * @param mijs - MultiInfrastructureJobSubmission class
     * @param inputFiles - JSONObject containing input files
     * @param outputFiles - JSONObject containing output files
     */
    private  void prepareIOSandbox(
             final MultiInfrastructureJobSubmission mijs,
             final JSONArray inputFiles,
             final JSONArray outputFiles) {

        // InputSandbox
        String inputSandbox = "";

        for (int i = 0; i < inputFiles.length(); i++) {
            JSONObject inputEntry = inputFiles.getJSONObject(i);

            if (inputEntry.getString("name").length() > 0) {
                String comma = (i == 0) ? "" : ",";

                inputSandbox += comma + gedCommand.getActionInfo()
                        + "/" + inputEntry.getString("name");
            }
        }

        mijs.setInputFiles(inputSandbox);
        LOG.debug("inputSandbox: '" + inputSandbox + "'");

        // OutputSandbox
        String outputSandbox = "";

        for (int i = 0; i < outputFiles.length(); i++) {
            JSONObject outputEntry = outputFiles.getJSONObject(i);

            if (outputEntry.getString("name").length() > 0) {
                String comma = (i == 0) ? "" : ",";

                outputSandbox += comma + outputEntry.getString("name");
            }
        }

        mijs.setOutputFiles(outputSandbox);
        LOG.debug("outputSandbox: '" + outputSandbox + "'");
    }

    /**
     * Prepares JobDescription specified in JSONObject item to setup the given
     * MultiInfrastructureJobSubmission object.
     *
     * @param  mijs - MultiInfrastructureJobSubmission object instance
     * @param geJobDescription - Object describing the job description
     * @see MultiInfrastructureJobSubmission
     */
    private void prepareJobDescription(
            final MultiInfrastructureJobSubmission mijs,
            final JSONObject geJobDescription) {

        // Job description
        mijs.setExecutable(geJobDescription.getString("executable"));
        mijs.setJobOutput(geJobDescription.getString("output"));
        mijs.setArguments(geJobDescription.getString("arguments"));
        mijs.setJobError(geJobDescription.getString("error"));
        mijs.setOutputPath(gedCommand.getActionInfo());
    }

    /**
     * Retrieve the APIServerDaemon PATH to the GridEngineLogConfig.xml file and
     * setup the GridEngineLogConfig.path environment variable accordingly This
     * variable will be taken by GridEngine while building up its log.
     */
    private void setupGELogConfig() {
        URL geLogConfig = this.getClass().
                getResource("GridEngineLogConfig.xml");
        String geLogConfigEnvVar = geLogConfig.getPath();

        LOG.debug("GridEngineLogConfig.xml at '" + geLogConfigEnvVar + "'");

        Properties props = System.getProperties();

        props.setProperty("GridEngineLogConfig.path", geLogConfigEnvVar);
        System.setProperties(props);
    }

    /**
     * Retrieve the id field of the ActiveGridInteraction table starting from
     * the jobDesc table.
     *
     * @return UsersTrackingDB ActiveGridInteraction record id
     */
    public final int getAGIId() {
        int agiId = 0;
        GridEngineInterfaceDB geiDB = null;

        LOG.debug("Getting ActiveGridInteraciton' id field for task: "
                + gedCommand.getTaskId());

        try {
            geiDB = new GridEngineInterfaceDB(utdbHost,
                                              utdbPort,
                                              utdbUser,
                                              utdbPass,
                                              utdbName);
            agiId = geiDB.getAGIId(gedCommand);
        } catch (Exception e) {
            LOG.fatal("Unable get id:" + LS + gedCommand + LS + e.toString());
        }

        return agiId;
    }

    /*
     * GridEngine interfacing methods
     */

    /**
     * Load GridEngineDaemon configuration settings.
     *
     * @param config - GridEngineDaemon configuration object
     */
    public final void setConfig(final APIServerDaemonConfig config) {
        this.gedConfig = config;

        // Extract class specific configutation
        this.utdbJNDI = config.getGridEngineDBjndi();
        this.utdbHost = config.getGridEngineDBhost();
        this.utdbPort = config.getGridEngineDBPort();
        this.utdbUser = config.getGridEngineDBuser();
        this.utdbPass = config.getGridEngineDBPass();
        this.utdbName = config.getGridEngineDBName();
        LOG.debug("GridEngineInterface config:" + LS
                + "  [UsersTrackingDB]" + LS
                + "    db_jndi: '" + this.utdbJNDI + "'" + LS
                + "    db_host: '" + this.utdbHost + "'" + LS
                + "    db_port: '" + this.utdbPort + "'" + LS
                + "    db_user: '" + this.utdbUser + "'" + LS
                + "    db_pass: '" + this.utdbPass + "'" + LS
                + "    db_name: '" + this.utdbName + "'" + LS);
    }

    /**
     * Setup machine IP address, needed by job submission.
     */
    private void getIP() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            byte[] ipAddr = addr.getAddress();
            int i = 0;
            gedIPAddress = "" + (short) (ipAddr[i++] & FF_BITMASK)
                        + ":" + (short) (ipAddr[i++] & FF_BITMASK)
                        + ":" + (short) (ipAddr[i++] & FF_BITMASK)
                        + ":" + (short) (ipAddr[i++] & FF_BITMASK);
        } catch (Exception e) {
            gedIPAddress = "";
            LOG.fatal("Unable to get the portal IP address");
        }
    }

    /**
     * Retrieve the id field of the ActiveGridInteraction table starting from
     * the jobDesc table.
     * @return Job description
     *
     */
    public final String getJobDescription() {
        String jobDesc = "";
        GridEngineInterfaceDB geiDB = null;

        LOG.debug("Getting jobDescription for AGI_id: "
                + gedCommand.getTargetId());

        try {
            geiDB = new GridEngineInterfaceDB(utdbHost,
                                              utdbPort,
                                              utdbUser,
                                              utdbPass,
                                              utdbName);
            jobDesc = geiDB.getJobDescription(gedCommand.getTargetId());
        } catch (Exception e) {
            LOG.fatal("Unable get job description for command:" + LS
                    + gedCommand + LS
                    + e.toString());
        }

        return jobDesc;
    }

    /**
     * submit the job identified by the gedCommand values.
     *
     * @return 0 - Ideally it should report the job id (not implemented in GE)
     */
    public final int jobSubmit() {
        int agiId = 0;

        LOG.debug("Submitting job");

        // MultiInfrastructureJobSubmission object
        MultiInfrastructureJobSubmission mijs = null;

        if ((utdbJNDI != null) && !utdbJNDI.isEmpty()) {
            mijs = new MultiInfrastructureJobSubmission();
        } else {
            mijs = new MultiInfrastructureJobSubmission(
                    "jdbc:mysql://" + utdbHost
                  + ":" + utdbPort
                  + "/" + utdbName,
                  utdbUser,
                  utdbPass);
        }

        if (mijs == null) {
            LOG.error("mijs is NULL, sorry!");
        } else {
            try {
                LOG.debug("Loading GridEngine job JSON desc");

                // Load <task_id>.json file in memory
                JSONObject geJobDesc = mkGEJobDesc();

                // application
                int geAppId =
                        geJobDesc.getInt("application");

                // commonName (user executing task)
                String geCommonName =
                        geJobDesc.getString("commonName");

                // infrastructure
                JSONObject geInfrastructure =
                        geJobDesc.getJSONObject("infrastructure");

                // jobDescription
                JSONObject geJobDescription =
                        geJobDesc.getJSONObject("jobDescription");

                // credentials
                JSONObject geCredentials =
                        geJobDesc.getJSONObject("credentials");

                // identifier
                String jobIdentifier =
                        geJobDesc.getString("identifier");

                // inputFiles
                JSONArray inputFiles =
                        geJobDesc.getJSONArray("input_files");

                // outputFiles
                JSONArray outputFiles =
                        geJobDesc.getJSONArray("output_files");

                // Loaded essential JSON components; now go through
                // each adaptor specific setting:
                // resourceManagers
                String resourceManagers =
                        geInfrastructure.getString("resourceManagers");
                String adaptor = resourceManagers.split(":")[0];

                LOG.info("Adaptor is '" + adaptor + "'");

                InfrastructureInfo[] infrastructures =
                        new InfrastructureInfo[1];

                // eTokenServer variables for GSI based infrastructures
                String eTokenHost;
                String eTokenPort;
                String eTokenId;
                String voms;
                String vomsRole;
                String rfcProxy;

                /*
                 * Each adaptor has its own specific settings Different adaptors
                 * may have in common some settings such as I/O Sandboxing, job
                 * description etc
                 */
                switch (adaptor) {

                // SSH Adaptor
                case "ssh":
                    try {
                        LOG.info("Entering SSH adaptor ...");

                        // Credential values
                        String username = geCredentials.getString("username");
                        String password = geCredentials.getString("password");
                        String[] sshEndPoint = {resourceManagers};

                        infrastructures[0] = new InfrastructureInfo(
                                resourceManagers,
                                "ssh",
                                username,
                                password,
                                sshEndPoint);
                        mijs.addInfrastructure(infrastructures[0]);

                        // Job description
                        prepareJobDescription(mijs, geJobDescription);

                        // IO Files
                        prepareIOSandbox(mijs, inputFiles, outputFiles);

                        // Submit asynchronously
                        agiId = 0;
                        mijs.submitJobAsync(geCommonName,
                                            gedIPAddress,
                                            geAppId,
                                            jobIdentifier);
                        LOG.debug("AGI_id: " + agiId);
                    } catch (Exception e) {
                        LOG.fatal("Caught exception:" + LS + e.toString());
                    }
                    break;

                // rOCCI Adaptor
                case "rocci":
                    LOG.info("Entering rOCCI adaptor ...");

                    // Infrastructure values
                    String protocol = "";
                    String secured = "";
                    String prefix = "";
                    String userData = "";
                    String link = "";
                    String waitms = "";
                    String waitsshms = "";
                    String sshport = "";
                    String osTpl =
                            geInfrastructure.getString("os_tpl");
                    String resourceTpl =
                            geInfrastructure.getString("resource_tpl");
                    String attributesTitle =
                            geInfrastructure.getString("attributes_title");
                    // Infrastructure parameters that couldn't be specified
                    try {
                        protocol = geInfrastructure.getString("protocol");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    try {
                        secured = geInfrastructure.getString("secured");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    try {
                        userData = geInfrastructure.getString("user_data");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    try {
                        prefix = geInfrastructure.getString("prefix");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    try {
                        link = geInfrastructure.getString("link");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    try {
                        waitms = geInfrastructure.getString("waitms");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    try {
                        waitsshms = geInfrastructure.getString("waitsshms");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    try {
                        sshport = geInfrastructure.getString("sshport");
                    } catch (JSONException e) {
                        LOG.warn("Non mandatory value exception: "
                                + e.toString());
                    }

                    // Credential values
                    eTokenHost = geCredentials.getString("eToken_host");
                    eTokenPort = geCredentials.getString("eToken_port");
                    eTokenId = geCredentials.getString("eToken_id");
                    voms = geCredentials.getString("voms");
                    vomsRole = geCredentials.getString("voms_role");
                    rfcProxy = geCredentials.getString("rfc_proxy");

                    // Building option statements
                    String prefixOpt =
                            (prefix.length() > 0)
                            ? "prefix=" + prefix + "&" : "";
                    String mixinResTpl =
                            "mixin_resource_tpl=" + resourceTpl + "&";
                    String mixinOsTpl =
                            "mixin_os_tpl=" + osTpl + "&";
                    String attributeTitle =
                            "attributes_title=" + attributesTitle + "&";
                    String protocolOpt =
                            (protocol.length() > 0)
                            ? "prptocol=" + protocol + "&" : "";
                    String securedFlag =
                            (secured.length() > 0)
                            ? "secured=" + secured + "&" : "";
                    String userDataOpt =
                            (userData.length() > 0)
                            ? "user_data=" + userData + "&" : "";
                    String linkOpt =
                            (link.length() > 0)
                            ? "link=" + link + "&" : "";
                    String waitmsOpt =
                            (waitms.length() > 0)
                            ? "waitms=" + waitms + "&" : "";
                    String waitsshmsOpt =
                            (waitsshms.length() > 0)
                            ? "waitsshms=" + waitms + "&" : "";
                    String sshportOpt =
                            (sshport.length() > 0)
                            ? "sshport=" + sshport + "&" : "";

                    // Generate the rOCCI endpoint
                    String[] rOCCIResourcesList = {
                        resourceManagers + "/?" + prefixOpt
                            + "action=create&"
                            + "resource=compute&" + mixinResTpl
                            + mixinOsTpl + attributeTitle + protocolOpt
                            + securedFlag + userDataOpt + linkOpt
                            + waitmsOpt + waitsshmsOpt + sshportOpt
                            + "auth=x509" };

                    LOG.info("rOCCI endpoint: '"
                            + rOCCIResourcesList[0] + "'");

                    // Prepare the infrastructure
                    infrastructures[0] = new InfrastructureInfo(
                              resourceManagers, // Infrastruture
                            "rocci", // Adaptor
                            "", //
                            rOCCIResourcesList, // Resources list
                            eTokenHost, // eTokenServer host
                            eTokenPort, // eTokenServer port
                            eTokenId, // eToken id (md5sum)
                            voms, // VO
                            vomsRole, // VO.group.role
                            rfcProxy.equalsIgnoreCase("true") // ProxyRFC
                    );
                    mijs.addInfrastructure(infrastructures[0]);

                    // Setup JobDescription
                    prepareJobDescription(mijs, geJobDescription);

                    // I/O Sandbox
                    // In rOCCI output and error files have to be removed
                    // from outputFiles array replacing the file name
                    // with an empty string
                    for (int i = 0; i < outputFiles.length(); i++) {
                        JSONObject outputEntry =
                                outputFiles.getJSONObject(i);

                        if (outputEntry.getString("name").equals(
                                geJobDescription.getString("output"))
                         || outputEntry.getString("name").equals(
                                geJobDescription.getString("error"))) {
                            LOG.debug("Skipping unnecessary file: '"
                                    + outputEntry.getString("name") + "'");
                            outputFiles.getJSONObject(i).put("name", "");
                        }
                    }

                    prepareIOSandbox(mijs, inputFiles, outputFiles);

                    // Submit asynchronously
                    agiId = 0;
                    mijs.submitJobAsync(geCommonName,
                                        gedIPAddress,
                                        geAppId,
                                        jobIdentifier);
                    LOG.debug("AGI_id: " + agiId);
                    break;

                // wms adaptor (EMI/gLite)
                case "wms":
                    LOG.info("Entering wms adaptor ...");

                    // Infrastructure values
                    String infraName = geInfrastructure.getString("name");

                    LOG.info("infrastructure name: '" + infraName + "'");

                    String bdii = geInfrastructure.getString("bdii");

                    LOG.info("bdii: '" + bdii + "'");

                    // ceList, jdlRequirements and swtags are not mandatory
                    // catch JSONException exception if these values are
                    // missing
                    String[] ceList = null;

                    try {
                        ceList = geInfrastructure.getString(
                                "ce_list").split(",");

                        if ((ceList != null) && (ceList.length > 0)) {
                            LOG.info("ce_list:");

                            for (int i = 0; i < ceList.length; i++) {
                                LOG.info("CE[" + i + "]: '"
                                        + ceList[i] + "'");
                            }
                        }
                    } catch (JSONException e) {
                        LOG.warn("NO CE list specified");
                    }

                    String[] jdlRequirements = null;

                    try {
                        jdlRequirements = geInfrastructure.getString(
                                "jdlRequirements").split(";");
                    } catch (JSONException e) {
                        LOG.warn("jdlRequirements not specified");
                    }

                    String swtags = null;

                    try {
                        swtags = geInfrastructure.getString("swtags");
                    } catch (JSONException e) {
                        LOG.warn("swtags not specified");
                    }

                    // Credentials values
                    eTokenHost = geCredentials.getString("eToken_host");
                    eTokenPort = geCredentials.getString("eToken_port");
                    eTokenId = geCredentials.getString("eToken_id");
                    voms = geCredentials.getString("voms");
                    vomsRole = geCredentials.getString("voms_role");
                    rfcProxy = geCredentials.getString("rfc_proxy");

                    // In wms case resourceManager could contain more than
                    // one wms:// entrypoint specified by a comma separated
                    // string
                    String[] wmsList = resourceManagers.split(",");

                    LOG.info("Creating Infrastrcuture object");
                    infrastructures[0] = new InfrastructureInfo(
                             infraName, // Infrastructure name
                            "wms", // Adaptor
                            wmsList, // List of wmses
                            eTokenHost, // eTokenServer host
                            eTokenPort, // eTokenServer port
                            eTokenId, // eToken id (md5sum)
                            voms, // VO
                            vomsRole, // VO.group.role
                            (null != swtags) ? swtags : "" // Software Tags
                    );

                    // Select one of the available CEs if specified
                    // in the ceList. The selection will be done
                    // randomly
                    if ((ceList != null) && (ceList.length > 0)) {
                        Random rndGen = new Random();
                        int selCEindex = rndGen.nextInt(ceList.length);

                        mijs.setJobQueue(ceList[selCEindex]);
                        LOG.info("Selected CE from the list: '"
                                + ceList[selCEindex] + "'");
                    } else {
                        LOG.info("No CE list specified, wms will choose");
                    }

                    // Specify infrastructure
                    mijs.addInfrastructure(infrastructures[0]);

                    // Setup JobDescription
                    prepareJobDescription(mijs, geJobDescription);

                    // I/O Sandbox
                    // In wms output and error files have to be removed
                    // from outputFiles array replacing the file name
                    // with an empty string
                    for (int i = 0; i < outputFiles.length(); i++) {
                        JSONObject outputEntry =
                                outputFiles.getJSONObject(i);

                        if (outputEntry.getString("name").equals(
                                geJobDescription.getString("output"))
                         || outputEntry.getString("name").equals(
                                 geJobDescription.getString("error"))) {
                            LOG.debug("Skipping unnecessary file: '"
                                    + outputEntry.getString("name") + "'");
                            outputFiles.getJSONObject(i).put("name", "");
                        }
                    }

                    prepareIOSandbox(mijs, inputFiles, outputFiles);

                    // JDL requirements
                    if ((jdlRequirements != null)
                     && (jdlRequirements.length > 0)) {
                        mijs.setJDLRequirements(jdlRequirements);
                    }

                    // Submit asynchronously
                    agiId = 0;
                    mijs.submitJobAsync(geCommonName,
                                        gedIPAddress,
                                        geAppId,
                                        jobIdentifier);
                    LOG.debug("AGI_id: " + agiId);
                    break;

                default:
                    LOG.fatal("Unrecognized or unsupported adaptor found!");
                }
            } catch (IOException e) {
                LOG.fatal("Unable to load APIServer JSON job description\n"
                        + LS + e.toString());
            } catch (Exception e) {
                LOG.fatal("Unable to submit job: " + LS + e.toString());
            }
        }

        return agiId;
    }

    /**
     * Prepares the jobOuput for the APIServer.
     *
     * @return Directory containing output files
     */
    public final String prepareJobOutput() {
        String jobDescription = getJobDescription();
        String tgzFileName = gedCommand.getActionInfo() + "/jobOutput/"
                + JSagaJobSubmission.removeNotAllowedCharacter(
                        jobDescription + "_"
                      + gedCommand.getTargetId() + ".tgz");

        LOG.debug("tgzFileName: '" + tgzFileName + "'");

        try {
            Process unpackTar = Runtime.getRuntime()
                    .exec("tar xzvf " + tgzFileName
                            + " -C " + gedCommand.getActionInfo());

            unpackTar.waitFor();
        } catch (Exception e) {
            LOG.fatal("Error extracting archive: " + tgzFileName);
        }

        return JSagaJobSubmission.removeNotAllowedCharacter(
                jobDescription + "_" + gedCommand.getTargetId());
    }

    /**
     * removeAGIRecord(int agiId).
     * This method removes the specified ActiveGridInteraction record form the
      * GridEngine' UsersTracking database.
      * @param agiId - GridEngine ActiveGridInteractions record identifier
     */
    public final void removeAGIRecord(final int agiId) {
        GridEngineInterfaceDB geiDB = null;

        LOG.debug("Removing record from ActiveGridInteraction with id: '"
                + agiId + "'");

        try {
            geiDB = new GridEngineInterfaceDB(utdbHost,
                                              utdbPort,
                                              utdbUser,
                                              utdbPass,
                                              utdbName);
            geiDB.removeAGIRecord(agiId);
        } catch (Exception e) {
            LOG.fatal("Unable delete ActiveGridInteraction entry for id "
                    + agiId + "command" + LS + e.toString());
        }
    }
}
