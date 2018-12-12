
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.io.*" %>
<%@ page import="org.json.simple.*" %>
<%@ page import="java.io.File"%>
<%@page import="com.google.gson.Gson"%>
<%@page import="com.google.gson.GsonBuilder" %>
<%@page import="org.json.simple.parser.JSONParser" %>

<%@ page import="javax.servlet.ServletContext" %>
<%@ page import="javax.servlet.*" %>

<%
  BufferedReader breader;
  String Longitude="";
  String Latitude="";
  ServletContext context =  getServletContext();
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
  try{
    breader=new BufferedReader(new FileReader(path));
    String line;
    StringBuilder JSONbuilder=new StringBuilder("");
    while ((line=breader.readLine())!=null)
    {
        JSONbuilder.append(line);
    }
    breader.close();
    JSONParser parser=new JSONParser();
    JSONObject jsonObject=null;
    try
    {
        jsonObject=(JSONObject)parser.parse(JSONbuilder.toString());
    }catch (Exception e){
        e.printStackTrace();
    }
    Longitude=(String)jsonObject.get("Longitude");
    Latitude=(String)jsonObject.get("Latitude");
  }catch (Exception e)
  {
      e.printStackTrace();
  }
  response.setIntHeader("Refresh", 4);
%>

<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>View User Location</title>

  <style>
    .Longitude input
    {
      width: 300px;
      height: 30px;
      border:2px solid black;
      font: bold 15px italic;
      font-style:italic;
      position:absolute;
      top:41%;
      right:55%;
    }

    .Longitude label
    {
      width: 100px;
      height: 42px;
      border:2px;
      font: bold 15px italic;
      font-style:italic;
      position:absolute;
      top:42%;
      right:75%;
      color: red;
    }
    .Latitude input
    {
      width: 300px;
      height: 30px;
      border:2px solid black;
      font: bold 15px italic;
      font-style:italic;
      position:absolute;
      top:41%;
      right:15%;
    }

    .Latitude label
    {
      width: 100px;
      height: 42px;
      border:2px;
      font: bold 15px italic;
      font-style:italic;
      position:absolute;
      top:42%;
      right:560px;
      color: red;
    }
  </style>
</head>

<body background="Material/Background2_Flowers.png">
<div class="Longitude">
  <input name="Longitude" type="text" id="Longitude" value=<%= Longitude%>>
  <label for="Longitude">Longitude</label>
</div>
<div class="Latitude">
  <input name="Latitude" type="text" id="Latitude" value=<%= Latitude%>>
  <label for="Latitude">Latitude</label>
</div>
</body>
</html>