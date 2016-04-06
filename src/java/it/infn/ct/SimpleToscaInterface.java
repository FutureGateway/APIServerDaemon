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



import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ogf.saga.context.Context;
import org.ogf.saga.context.ContextFactory;
import org.ogf.saga.error.NotImplementedException;
import org.ogf.saga.error.IncorrectStateException;
import org.ogf.saga.error.NoSuccessException;
import org.ogf.saga.error.PermissionDeniedException;
import org.ogf.saga.error.SagaException;
import org.ogf.saga.session.Session;
import org.ogf.saga.session.SessionFactory;
import org.ogf.saga.job.JobDescription;
import org.ogf.saga.job.JobService;
import org.ogf.saga.job.JobFactory;
import org.ogf.saga.job.Job;
import org.ogf.saga.task.State;
import org.ogf.saga.url.URL;
import org.ogf.saga.url.URLFactory;
import fr.in2p3.jsaga.impl.job.instance.JobImpl;
import fr.in2p3.jsaga.impl.job.service.JobServiceImpl;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
//import java.net.URL;
//import java.net.InetAddress;
//import java.nio.charset.Charset;
//import java.util.Properties;
import java.util.Random;
import org.json.*;
import org.apache.commons.io.IOUtils;

/**
 * This class interfaces any call to the GridEngine library
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
public class SimpleToscaInterface {
    /*
      Logger
    */  
    private static final Logger _log = Logger.getLogger(SimpleToscaInterface.class.getName());
    
    public static final String LS = System.getProperty("line.separator");

    APIServerDaemonCommand toscaCommand;
    APIServerDaemonConfig asdConfig;
    String APIServerConnURL="";
 
    /**
     * Empty constructor for SimpleToscaInterface
     */
    public SimpleToscaInterface() {
        _log.debug("Initializing SimpleToscaInterface");
    }
    
    /**
     * Constructor for SimpleTosca taking as input a given command
     */
    public SimpleToscaInterface(APIServerDaemonCommand toscaCommand) {
        this();
        _log.debug("SimpleTosca command:"+LS+toscaCommand);
        this.toscaCommand=toscaCommand;        
    }

    /**
     * Constructor for SimpleToscaInterface taking as input the
     * APIServerDaemonConfig and a given command
     */
    public SimpleToscaInterface(APIServerDaemonConfig asdConfig
                              ,APIServerDaemonCommand toscaCommand) {
        this(toscaCommand);
        setConfig(asdConfig);
    }

    /**
     * Load GridEngineDaemon configuration settings
     * @param Config GridEngineDaemon configuration object
     */
    public void setConfig(APIServerDaemonConfig asdConfig) {
        this.asdConfig=asdConfig;
        this.APIServerConnURL = asdConfig.getApisrv_URL();               
    }

    /**
     * process JSON object containing information stored in file:
     * <action_info>/<task_id>.json and submit using tosca adaptor
     */    
    public int submitTosca() {
        int toscaId = 0;
        JSONObject jsonJobDesc = null;
        
        _log.debug("Entering submitSimpleTosca");
        
        String jobDescFileName = toscaCommand.getActionInfo()+"/"
                                +toscaCommand.getTaskId()
                                +".json"; 
        _log.debug("JSON filename: '"+jobDescFileName+"'");
        try {
            InputStream is = new FileInputStream(jobDescFileName);
            String jsonTxt = IOUtils.toString(is);            
            jsonJobDesc = (JSONObject) new JSONObject(jsonTxt);            
            _log.debug("Loaded APIServer JobDesc:\n"+LS+jsonJobDesc);

            // Now read values from JSON and prepare the submission accordingly
            // Username (unused yet but later used for accounting)
            String user = String.format("%s", jsonJobDesc.getString("user"));  
            // Prepare JSAGA IO file list
            String IOFiles="";
            JSONArray inputFiles=jsonJobDesc.getJSONArray("input_files");
            for(int i=0; i<inputFiles.length(); i++) {
                JSONObject fileEntry = inputFiles.getJSONObject(i);
                String fileName = fileEntry.getString("name");
                IOFiles += (IOFiles.length()>0?",":"")
                          +toscaCommand.getActionInfo()+"/"
                          +fileEntry.getString("name")+">"+fileEntry.getString("name");
            } 
            JSONArray outputFiles=jsonJobDesc.getJSONArray("output_files");
            for(int j=0; j<inputFiles.length(); j++) {
                JSONObject fileEntry = inputFiles.getJSONObject(j);
                String fileName = fileEntry.getString("name");
                IOFiles += (IOFiles.length()>0?",":"")
                          +toscaCommand.getActionInfo()+"/jobOutput/"
                          +fileEntry.getString("name")+"<"+fileEntry.getString("name");
            }
            String files[] = IOFiles.split(",");
            for(int i=0; i<files.length; i++)
              _log.debug("IO Files["+i+"]: '"+files[i]+"'");

            // Get app Info and Parameters
            JSONObject appInfo = new JSONObject();
            appInfo = jsonJobDesc.getJSONObject("application");
            JSONArray appParams = new JSONArray();
            appParams = appInfo.getJSONArray("parameters");

            // Application parameters
            String executable = "";
            String output = "";
            String error = "";
            for(int i=0; i<appParams.length(); i++) {
                JSONObject appParameter = appParams.getJSONObject(i);
                // Get parameter name and value
                String param_name  = appParameter.getString("param_name");
                String param_value = appParameter.getString("param_value");
                switch(param_name) {

                    case "jobdesc_executable":
                        executable=param_value;
                        _log.debug("executable: '"+executable+"'");
                        break;
                    case "jobdesc_output":
                        output=param_value;
                        _log.debug("output: '"+output+"'");
                        break;
                    case "jobdesc_error":
                        error=param_value;
                        _log.debug("error: '"+error+"'");
                        break;
                    default:
                        _log.warn("Unsupported application parameter name: '"+param_name+"' with value: '"+param_value+"'");
                }
            }
            // Arguments
            String job_args="";
            JSONArray jobArguments = jsonJobDesc.getJSONArray("arguments");
            for(int j=0; j<jobArguments.length(); j++)
                job_args += (job_args.length()>0?",":"") + jobArguments.getString(j);
            String[] args = job_args.split(",");
            for(int k=0; k<args.length; k++)
                _log.debug("args["+k+"]: '"+args[k]+"'");

            // Infrastructures
            // Select one of the possible infrastructures among the enabled ones
            // A random strategy is currently implemented; this could be changed later
            JSONArray jobInfrastructures = appInfo.getJSONArray("infrastructures");
            JSONArray enabledInfras = new JSONArray();
            for(int v=0,w=0; w<jobInfrastructures.length(); w++) {
                JSONObject infra = jobInfrastructures.getJSONObject(w);
                if(infra.getString("status").equals("enabled"))
                    enabledInfras.put(v++,infra);
            }
            int selInfraIdx = 0;
            Random rndGen = new Random();
            if(enabledInfras.length()>1)
                selInfraIdx = rndGen.nextInt(enabledInfras.length());
            JSONObject selInfra = new JSONObject();
            selInfra = enabledInfras.getJSONObject(selInfraIdx);
            _log.debug("Selected infra: '"+LS+selInfra.toString(4)+"'"); 

            // Infrastructure parameters
            String toscaEndPoint = "";
            String toscaTemplate = "";
            String token = "";
            JSONArray infraParams = selInfra.getJSONArray("parameters");
            for(int h=0; h<infraParams.length(); h++) {
                JSONObject infraParameter = infraParams.getJSONObject(h);
                String param_name = infraParameter.getString("name");
                String param_value = infraParameter.getString("value");
                switch(param_name) {
                    case "tosca_endpoint":
                        toscaEndPoint = param_value;
                        _log.debug("tosca_endpoint: '"+toscaEndPoint+"'");
                        break;
                    case "tosca_token":
                        token = param_value;
                        _log.debug("tosca_token: '"+token+"'");
                        break;
                    case "tosca_template":
                        toscaTemplate = toscaCommand.getActionInfo()+"/"+param_value;
                        _log.debug("tosca_template: '"+toscaTemplate+"'");
                        break;
                    default:
                        _log.warn("Unsupported infrastructure parameter name: '"+param_name+"' with value: '"+param_value+"'");
                }    
            }

            // Finally submit the job
            String tosca_id = submitJob(token
                                       ,toscaEndPoint
                                       ,toscaTemplate
                                       ,executable
                                       ,output
                                       ,error
                                       ,args
                                       ,files);
        
            _log.info("tosca_id: '"+tosca_id+"'");

            // Register JobId, if targetId exists it is a submission retry
            SimpleToscaInterfaceDB stiDB = null;
            try {
                stiDB = new SimpleToscaInterfaceDB(APIServerConnURL);
                if (toscaCommand.getTargetId() > 0) {
                    _log.debug("Updating existing entry in simple_tosca table at id: '"+toscaCommand.getTargetId()+"'");
                    int toscaTargetId=toscaCommand.getTargetId();
                    stiDB.updateToscaId(toscaTargetId,tosca_id);
                } else {
                    _log.debug("Creating a new entry in simple_tosca table for submission: '"+tosca_id+"'");
                    toscaId = stiDB.registerToscaId(toscaCommand,tosca_id);
                }
            } catch (Exception e) {          
                _log.fatal("Unable to register tosca_id: '"+tosca_id+"'");
            }
            finally {
                if(stiDB!=null) stiDB.close(); 
            }

        } catch (Exception ex) {
           _log.error("Caught exception: '"+ex.toString()+"'");
        }

        return toscaId;
    }

    /**
     * Submit tosca job
     */
    public String submitJob(String token
                           ,String toscaEndPoint
                           ,String toscaTemplate
                           ,String executable
                           ,String output
                           ,String error
                           ,String[] args
                           ,String[] files) {
        Session session = null;
        Context context = null;        
        JobService service = null; 
        Job job = null;
        String ServiceURL = "";
        String jobId = "";        
        
        System.setProperty("saga.factory", "fr.in2p3.jsaga.impl.SagaFactoryImpl");
 
        try {
            session = SessionFactory.createSession(false);            
            context = ContextFactory.createContext("tosca");

            //Set the public key for SSH connections
            context.setAttribute("UserPublicKey",
                    System.getProperty("user.home") + 
                    System.getProperty("file.separator") + 
                    ".ssh/id_rsa.pub");
            
            //Set the private key for SSH connections
            context.setAttribute("UserPrivateKey",
                    System.getProperty("user.home") + 
                    System.getProperty("file.separator") + 
                    ".ssh/id_rsa");

            context.setAttribute("token",token);                        
            session.addContext(context);
            
            try {    
                _log.info("Initialize the JobService context... ");
                ServiceURL = toscaEndPoint + "?" + "tosca_template=" + toscaTemplate;
                URL serviceURL = URLFactory.createURL(ServiceURL);
                _log.info("Tosca ServiceURL = '" + serviceURL +"'");
                
                service = JobFactory.createJobService(session, serviceURL);  
                JobDescription desc = JobFactory.createJobDescription();
                _log.info("Setting up tosca attributes ...");
                desc.setAttribute(JobDescription.EXECUTABLE, executable);                	           
                desc.setAttribute(JobDescription.OUTPUT, output);
                desc.setAttribute(JobDescription.ERROR, error);                  
                _log.info("Setting up tosca verctor attributes ...");
                desc.setVectorAttribute(JobDescription.ARGUMENTS, args); 
                desc.setVectorAttribute(desc.FILETRANSFER, files);
                _log.info("Creating job ...");
                job = service.createJob(desc);
                _log.info("Submit job ...");
                job.run();                                

                // Getting the jobId
                jobId = job.getAttribute(Job.JOBID);
                _log.info("Job instance created with jobId: '"+jobId+"'");                

                try {
                        ((JobServiceImpl)service).disconnect();                        
                } catch (NoSuccessException ex) {
                        _log.error("See below the stack trace... ");
                        ex.printStackTrace(System.out);					
                }
                _log.info("Closing session...");
                session.close();   
            } catch (Exception ex) {
                    _log.error("Failed to initialize the JobService");
                    _log.error("See below the stack trace... ");                
                    ex.printStackTrace(System.out);
            }                                           
        } catch (Exception ex) {
            _log.error("Failed to initialize the security context"+LS
                     +"See below the stack trace... "
                     );
            ex.printStackTrace(System.out);
        }        
        return jobId;
    }

    /**
     *
     */
    public String getToscaId() {
        String toscaId="";
        SimpleToscaInterfaceDB stiDB = null;
        try {
            stiDB = new SimpleToscaInterfaceDB(APIServerConnURL);
            _log.debug("GetToscaId for task_id: '"+toscaCommand.getTaskId()+"'");
            toscaId = stiDB.getToscaId(toscaCommand);
        } catch (Exception e) {
            _log.fatal("Unable to get tosca_id for task_id: '"+toscaCommand.getTaskId()+"'");
        }
        finally {
            if(stiDB!=null) stiDB.close();
        }
        _log.debug("tosca_id: '"+toscaId+"'");
        return toscaId;
    }
    
    /**
     *
     */
    public String getStatus() {
        _log.info("getStatus");
        Session session = null;
        Context context = null;
        JobService service = null;
        Job job = null;
        String ServiceURL = "";
        String jobId = "";
        String status="";

        System.setProperty("saga.factory", "fr.in2p3.jsaga.impl.SagaFactoryImpl");
 
        try {
            session = SessionFactory.createSession(false);
            context = ContextFactory.createContext("tosca");           
            
            //Set the public key for SSH connections
            context.setAttribute("UserPublicKey",
                    System.getProperty("user.home") +
                    System.getProperty("file.separator") +
                    ".ssh/id_rsa.pub");

            //Set the private key for SSH connections
            context.setAttribute("UserPrivateKey",
                    System.getProperty("user.home") +
                    System.getProperty("file.separator") +
                    ".ssh/id_rsa");

            context.setAttribute("token","AABBCCDDEEFF00112233445566778899");            
            session.addContext(context);

            String toscaId=getToscaId();
            _log.debug("toscaId: '"+toscaId+"'");
            if(toscaId != null && toscaId.length() > 0) {
                ServiceURL = toscaId.substring(1,toscaId.indexOf("?"));            
                URL serviceURL = URLFactory.createURL(ServiceURL);                
                _log.debug("serviceURL = '" + serviceURL +"'");
                service = JobFactory.createJobService(session, serviceURL);  

                String nativeJobId = getNativeJobId(toscaId);
                job = service.getJob(nativeJobId);                                

                _log.debug("Fetching the status of the job: '" +toscaId+ "'");
                _log.debug("nativeJobId: '" + nativeJobId + "'");

                State state = null;

                try { 
                    state = job.getState();
                    status = state.name();
                    _log.debug("Current Status = '" + status + "'");
                    //String executionHosts[];
                    //executionHosts = job.getVectorAttribute(Job.EXECUTIONHOSTS);
                    //_log.debug("Execution Host = " + executionHosts[0]);
                } catch (Exception ex) {
                    _log.error("Error in getting job status... [ FAILED ] ");
                    _log.error(ex.toString());
                    _log.error("Cause :" + ex.getCause());
                }
                
                // Perform the right action related to its status  
                if (State.CANCELED.compareTo(state) == 0) {
                    _log.info("");
                    _log.info("Job Status = CANCELED ");
                } else if (State.FAILED.compareTo(state) == 0) {
                    try {
                            String exitCode = job.getAttribute(Job.EXITCODE);
                            _log.info("");
                            _log.info("Job Status = FAILED");
                            _log.info("Exit Code (" + exitCode + ")");
                    } catch (SagaException e) { 
                        _log.error("Job failed."); 
                    }
                } else if (State.DONE.compareTo(state) == 0) { 
                    String exitCode = "";
                    _log.debug("Status is done, getting EXITCODE");
                    try {
                        exitCode = job.getAttribute(Job.EXITCODE);    
                    } catch (Exception ex) {
                        _log.error("Unable to get exit code");
                        _log.debug(ex.toString());
                    }
                    _log.debug("Final Job Status = DONE");
                    _log.debug("Exit Code ("+exitCode+")");                    
                    _log.debug("Retrieving job results; this operation may take a few minutes to complete ...");

                    // ========================================== //
                    // === EXECUTING post-staging and cleanup === //
                    // ========================================== //
                    try { 
                        ((JobImpl)job).postStagingAndCleanup();
                        _log.info("Job outputs retrieved");
                    } catch (NotImplementedException   ex) { _log.error(ex.toString()); 
                    } catch (PermissionDeniedException ex) { _log.error(ex.toString()); 
                    } catch (IncorrectStateException   ex) { _log.error(ex.toString()); 
                    } catch (NoSuccessException        ex) { _log.error(ex.toString()); }
                    
                    try { 
                        ((JobServiceImpl)service).disconnect();
                        _log.debug("Service disconnected successfully");
                    } catch (NoSuccessException ex) { 
                        _log.debug("Service disconnected unsuccessfully");
                        _log.error("See below the stack trace... ");
                        _log.error(ex.toString()); 
                    }
                    session.close();
                    _log.debug("Session closed");
                } else {
                    _log.debug("Unexpected job status: '" + state + "'");                    
                }
            } else {
              _log.debug("Unable to get status for task_id: '"+toscaCommand.getTaskId()+"'");
            }
        } catch (Exception ex) {
            _log.error("Failed to initialize the security context"+LS
                      +"See below the stack trace... "
                      );
            _log.error(ex.toString());
        }
        _log.debug("status: '"+status+"'"); 
        return status;
    }

    /**
     * GetNativeJobId
     */
    private static String getNativeJobId(String jobId) 
    {
        String nativeJobId = "";
        Pattern pattern = Pattern.compile("\\[(.*)\\]-\\[(.*)\\]");
        Matcher matcher = pattern.matcher(jobId);
    
        try {
            if (matcher.find()) nativeJobId = matcher.group(2);                
            else return null;               
        } catch (Exception ex) { 
            System.out.println(ex.toString());
            return null;
        }

        return nativeJobId;
    }
}