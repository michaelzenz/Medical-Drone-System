## How to use

A servlet is built to receive msg from App. However, it is still primitive because websocket has not yet been used here, thus currently what it is doing is simply output the user location to a json, so that someone else can track the location through a webpage, which can read the local json and show it on the page  

The User Location is stored in temp directory.

## How to deploy

Go to out\artifacts\Server_war_exploded, do git push there and it will push the web application to michael`s server.

## where is the Server

At 18.221.10.160, http://18.221.10.160:8080/MedicalDrone/ReceiveGPSServlet is the servlet URL