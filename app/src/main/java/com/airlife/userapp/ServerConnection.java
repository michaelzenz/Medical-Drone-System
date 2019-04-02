package com.airlife.userapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;


//this class has integrated what you need to contact server
//you can see the server address from url
public class ServerConnection
{

    //go to http://3.16.180.60:8081/MedicalDrone/index.jsp for test result
    final private String local_url = "http://10.0.2.2:8080/Server_war_exploded/ReceiveGPSServlet";//for local debug
    final private String url = "http://3.16.180.60:8081/MedicalDrone/ReceiveGPSServlet";//servlet location

    private OkHttpClient client;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private onResponseReadyListener mlistener;


    public ServerConnection(onResponseReadyListener listener)
    {
        client = new OkHttpClient();
        mlistener=listener;
    }
    //used when receive msg from server
    public interface onResponseReadyListener
    {
        void onResponseReady(String Response_msg);
    }
    //call this method to send message, a thread will be created to listen from the server
    //add the user identity to the server
    public void SendGPSbyPost(String Longitude, String Latitude,String Action)
    {
        FormBody.Builder builder=new FormBody.Builder();
        builder.add("Longitude", Longitude);
        builder.add("Latitude", Latitude);
        builder.add("Identity", "User");
        builder.add("Action", Action);
        FormBody formBody=builder.build();
        Request request = new Request.Builder().url(url).post(formBody).build();

        Call call=client.newCall(request);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e)
            {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                byte[] bytes =  response.body().bytes();
                final String Response_msg = new String(bytes,"GBK");
                mlistener.onResponseReady(Response_msg);
            }
        });
    }

}
