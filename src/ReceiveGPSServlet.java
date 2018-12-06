import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.crypto.Data;

import org.json.simple.JSONObject;

public class ReceiveGPSServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public ReceiveGPSServlet() {
        super();

    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }
    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        // TODO Auto-generated method stub
        System.out.println("doPost");
        String GPS = request.getParameter("GPS");
        PrintWriter response_out = response.getWriter();

        ServletContext context = getServletContext();
        String contextPath = context.getRealPath(File.separator);
        JSONObject obj= new JSONObject();
        obj.put("UserLocation",GPS);
        String path;
        String system_name=System.getProperty("os.name");
        if(system_name.contains("Windows") || system_name.contains("windows"))
        {
            path = contextPath + "\\temp\\UserLocation.json";
        }
        else
        {
            path=contextPath+"/temp/UserLocation.json";
        }
        System.out.println(system_name);
        System.out.println(path);
        FileWriter out_file=new FileWriter(path);
        out_file.write(obj.toJSONString());
        out_file.close();

        try
        {
            Integer.parseInt(GPS);
            response_out.write("GPS Data Received, Current Location: "+GPS);

        }catch (NumberFormatException e){
            response_out.write("Login Fail");
        }

    }
}
