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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
        String Longitude = request.getParameter("Longitude");
        String Latitude  = request.getParameter("Latitude");
        PrintWriter response_out = response.getWriter();

        if(Longitude == null)Longitude="";
        if(Latitude == null)Latitude="";
        JSONObject obj= new JSONObject();
        String GPS="Longitude: "+Longitude+"  Latitude: "+Latitude;
        obj.put("Longitude",Longitude);
        obj.put("Latitude",Latitude);
        System.out.println(GPS);
        ServletContext context = getServletContext();
        String contextPath = context.getRealPath(File.separator);
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

        response_out.write("GPS Data Received, Current Location: "+GPS);

    }
}
