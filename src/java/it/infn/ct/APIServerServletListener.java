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
import java.sql.Connection;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.sql.DataSource;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;

/**
 * Servlet context listener object used to instantiate the APIServerDaemon class
 * and initalize the daemon ThreadPool and its polling thread.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 * @see APIServerDaemon
 */
public class APIServerServletListener implements ServletContextListener {
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");
     /**
      * File path separator.
      */
    private static final String PS = System.getProperty("file.separator");
    /**
     * User name.
     */
    private static final String US = System.getProperty("user.name");
    /**
     * Java runtime vendor.
     */
    private static final String VN = System.getProperty("java.vendor");
    /**
     * Java runtime version.
     */
    private static final String VR = System.getProperty("java.version");
    /**
     * APIServer daemin class instance.
     */
    private APIServerDaemon asDaemon = null;

    /**
     * Servlet context.
     */
    private ServletContext context;

    /**
     * Called while destroying servlet context.
     *
     * @param sce - Servlet context event
     * @see APIServerDaemon
     */
    @Override
    public final void contextDestroyed(final ServletContextEvent sce) {
        if (asDaemon != null) {
            asDaemon.shutdown();
        }

        // Unregister MySQL driver
        APIServerDaemonDB.unregisterDriver();

        // Notify termination
        System.out.println("--- APIServerDaemon Stopped ---");
    }

    /**
     * Called during servlet context initalization, it instantiate the
     * APIServerDaemon class.
     *
     * @param sce - Servlet context event object
     * @see APIServerDaemon
     */
    @Override
    public final void contextInitialized(final ServletContextEvent sce) {
        context = sce.getServletContext();

        String apiSrvDaemonPath = context.getRealPath(PS);
        System.setProperty("APISrvDaemonPath", context.getRealPath("/"));
        System.setProperty("APISrvDaemonVersion",
                           "v.0.0.2-22-g2338f25-2338f25-40");

        // Notify execution
        System.out.println("--- " + "Starting APIServerDaemon "
                + System.getProperty("APISrvDaemonVersion") + " ---");
        System.out.println("Java vendor : '" + VN + "'");
        System.out.println("Java vertion: '" + VR + "'");
        System.out.println("Running as  : '" + US + "' username");
        System.out.println("Servlet path: '" + apiSrvDaemonPath + "'");

        // Initialize log4j logging
        String log4jPropPath =
                apiSrvDaemonPath + "WEB-INF" + PS + "log4j.properties";
        File log4PropFile = new File(log4jPropPath);

        if (log4PropFile.exists()) {
            System.out.println("Initializing log4j with: " + log4jPropPath);
            PropertyConfigurator.configure(log4jPropPath);
        } else {
            System.err.println(
                    "WARNING: '" + log4jPropPath
                  + " 'file not found, so initializing log4j "
                  + "with BasicConfigurator");
            BasicConfigurator.configure();
        }

        // Make a test with jdbc/geApiServerPool
        // jdbc/UserTrackingPool
        // jdbc/gehibernatepool connection pools
        String currentPool = "not yet defined!";
        String poolPrefix = "java:/comp/env/";
        String[] pools = {"jdbc/fgApiServerPool",
                          "jdbc/UserTrackingPool",
                          "jdbc/gehibernatepool"};
        Connection[] connPools = new Connection[pools.length];

        for (int i = 0; i < pools.length; i++) {
            try {
                Context initContext = new InitialContext();
                Context envContext =
                        (Context) initContext.lookup("java:comp/env");

                currentPool = pools[i];

                // DataSource ds =
                // (DataSource)initContext.lookup(poolPrefix+currentPool);
                DataSource ds = (DataSource) envContext.lookup(currentPool);

                connPools[i] = ds.getConnection();
                System.out.println("PERFECT: " + currentPool + " was ok");
            } catch (Exception e) {
                System.err.println("WARNING: " + currentPool + " failed" + LS
                        + e.toString());
            } finally {
                try {
                    connPools[i].close();
                } catch (Exception e) {
                    System.err.println("WARNING: " + currentPool
                            + " failed to close" + LS + e.toString());
                }
            }
        }

        // Register MySQL driver
        APIServerDaemonDB.registerDriver();

        // Initializing the daemon
        if (asDaemon == null) {
            asDaemon = new APIServerDaemon();
        }

        asDaemon.startup();
    }
}
