import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.crypto.Data;



/**
 * Servlet implementation class LoginServlet
 */
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public LoginServlet() {
        super();

    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {


        System.out.println("doGet");
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if("123".equals(username) && "123".equals(password)){

            response.getOutputStream().write("Login Succeed".getBytes());
        }else{
            response.getOutputStream().write("Login Fail".getBytes());

            response.getOutputStream().write("登录成功".getBytes());
        }
    }
    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // TODO Auto-generated method stub
        System.out.println("doPost");

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if("123".equals(username) && "123".equals(password)){
            response.getOutputStream().write("Login Succeed".getBytes());
        }else{
            response.getOutputStream().write("Login Fail".getBytes());
        }

    }

}
