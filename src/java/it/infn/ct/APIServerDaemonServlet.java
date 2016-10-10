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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//import java.util.logging.Logger;
import org.apache.log4j.Logger;

/**
 * APIServerDaemonServlet Servlet used only to report APIServerDaemon execution
 * statistics and generic information.
 *
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
 */
@WebServlet(name = "APIServerDaemonServlet",
            urlPatterns = { "/configuration" })
public class APIServerDaemonServlet extends HttpServlet {

    /**
     * Logger object.
     */
    private static final Logger LOG =
            Logger.getLogger(APIServerDaemonServlet.class.getName());
    /**
     * Line separator constant.
     */
    public static final String LS = System.getProperty("line.separator");

    /**
     * Configurations from APIServerDaemon properties file.
     */
    private APIServerDaemonConfig asdConfig;

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods.
    // Click on the + sign on the left to edit the code.">

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request .Servlet request
     * @param response - Servlet response
     * @throws ServletException - If a servlet-specific error occurs
     * @throws IOException - If an I/O error occurs
     */
    @Override
    protected final void doGet(final HttpServletRequest request,
                               final HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
        LOG.info("GET: " + request);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request
     *            servlet request
     * @param response
     *            servlet response
     * @throws ServletException
     *             if a servlet-specific error occurs
     * @throws IOException
     *             if an I/O error occurs
     */
    @Override
    protected final void doPost(final HttpServletRequest request,
                                final HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
        LOG.info("POST: " + request);
    }

    /**
     * Servlet init function.
     *
     * @param config - APIServerDaemon configuration.
     * @throws ServletException - Servlet exception
     */
    @Override
    public final void init(final ServletConfig config)
            throws ServletException {
        // Read init parameters (web.xml)
        // String initParamValue = config.getInitParameter("initParamName");
        LOG.debug("Loading preferences for Servlet");
        asdConfig = new APIServerDaemonConfig(false);
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request - Servlet request
     * @param response - Servlet response
     * @throws ServletException - If a servlet-specific error occurs
     * @throws IOException - If an I/O error occurs
     */
    protected final void processRequest(final HttpServletRequest request,
                                        final HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        /*
         * try (PrintWriter out = response.getWriter()) { // TODO output your
         * page here. You may use following sample code. out.println(
         * "<!DOCTYPE html>"); out.println("<html>"); out.println("<head>");
         * out.println("<title>Servlet APIServerDaemonServlet</title>");
         * out.println("</head>"); out.println("<body>"); out.println(
         * "<h1>Servlet APIServerDaemonServlet at " + request.getContextPath()
         * + "</h1>"); out.println("<h2>APIServer configuration</h2>");
         * out.println("<p>"+asdConfig.toString()+"</p>");
         * out.println("</body>"); out.println("</html>"); }
         */

        // Context path
        request.setAttribute("contextPath",
                             request.getContextPath()); // This  will be
                                                        // available as
                                                        // ${message}

        // APIServerDaemon DB settings
        request.setAttribute("apisrv_dbhost", asdConfig.getApisrvDBHost());
        request.setAttribute("apisrv_dbport", asdConfig.getApisrvDBPort());
        request.setAttribute("apisrv_dbname", asdConfig.getApisrvDBName());
        request.setAttribute("apisrv_dbuser", asdConfig.getApisrvDBUser());
        request.setAttribute("apisrv_dbpass", asdConfig.getApisrvDBPass());

        // APIServerDaemon Threads settings
        request.setAttribute("asdMaxThreads", asdConfig.getMaxThreads());
        request.setAttribute("asdCloseTimeout", asdConfig.getCloseTimeout());

        // GridEngine DB Settings
        request.setAttribute("utdb_jndi", asdConfig.getGridEngineDBjndi());
        request.setAttribute("utdb_host", asdConfig.getGridEngineDBhost());
        request.setAttribute("utdb_port", asdConfig.getGridEngineDBPort());
        request.setAttribute("utdb_name", asdConfig.getGridEngineDBName());
        request.setAttribute("utdb_user", asdConfig.getGridEngineDBuser());
        request.setAttribute("utdb_pass", asdConfig.getGridEngineDBPass());

        // Render the HTML
        request.getRequestDispatcher("config.jsp").forward(request, response);
    }

    /**
     * Retrieves statistical information about APIServerDaemon activity.
     * In particular it provides:
     * - Start timestamp
     * - Current timestamp
     * - Number of elements in the queue
     * - Number of elements in the queue for each state
     *
     * @param connectionURL - APIServerDaemon database connection URL
     */
    final void getAPIServerDaemonStatInfo(final String connectionURL) {
        APIServerDaemonDB asdDB = null;

        try {
            LOG.debug("Opening connection for retry command");
            asdDB = new APIServerDaemonDB(connectionURL);
        } catch (Exception e) {
            LOG.fatal("Unable retry task related to given command:" + LS
                    + this.toString());
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public final String getServletInfo() {
        return "Servlet used only to report GridEngineDaemon "
                + "execution statistics and generic information";
    } // </editor-fold>
}
