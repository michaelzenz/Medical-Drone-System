package com.airlife.userapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ServerConnection.onResponseReadyListener{
    private ServerConnection connection;
    private Button btn_locate,btn_map,btn_reset, btn_pause, btn_start, btn_cancel;
    private TextView textView;
    private String action;

    float x1,x2,y1,y2;//check the swipe status

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connection=new ServerConnection(this);
        btn_locate = (Button) findViewById(R.id.btn_locate);
        btn_map = (Button) findViewById(R.id.btn_map);
        btn_reset=(Button) findViewById(R.id.btn_reset);
        btn_pause = (Button) findViewById(R.id.btn_pause);
        btn_start = (Button) findViewById(R.id.btn_start);
        btn_cancel = (Button) findViewById(R.id.btn_cancel);

        textView = (TextView) findViewById(R.id.textView);
        
        btn_pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action = "pause";
            }
        });

        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action = "Request";
            }
        });

        btn_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action = "Cancel";
            }
        });

        btn_locate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=
                        PackageManager.PERMISSION_GRANTED){
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},1);
                } else{
                    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    try {
                        String city = hereLocation(location.getLatitude(),location.getLongitude());
                        updateShow(location, city);
                        sendGPSLocation(location, connection, action);
                    } catch(Exception e){
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this,"Not Found!",Toast.LENGTH_SHORT).show();
                    }

                }

            }
        });

        //jump to google map API activity
        btn_map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(MainActivity.this, MapsActivity.class);
                startActivity(intent);
            }
        });

        btn_reset.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                connection.SendGPSbyPost("0.0","0.0" ,"Cancel" );
                action = "Cancel";
            }
        });
    }

// check if need to swipe to another page
    public boolean onTouchEvent(MotionEvent touchevent){
        switch(touchevent.getAction()){
            case MotionEvent.ACTION_DOWN:
                x1 = touchevent.getX();
                y1 = touchevent.getY();
                break;
            case MotionEvent.ACTION_UP:
                x2 = touchevent.getX();
                y2 = touchevent.getY();
                if(x1 < x2){
                    Intent i = new Intent(MainActivity.this, MapsActivity.class);
                    startActivity(i);
                }
                break;
        }
        return false;
    }

    //check if the permission is granted
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    try {
                        String city = hereLocation(location.getLatitude(),location.getLongitude());
                        updateShow(location,city);
                        sendGPSLocation(location, connection, action);
                    } catch(Exception e){
                        Toast.makeText(this,"Not Found!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this,"Permission is not Granted!", Toast.LENGTH_SHORT).show();
                }
        }
    }

    //this function returns the current city name
    private  String hereLocation(double lat, double lon){
        String cityName = "";

        Geocoder geocoder = new Geocoder(this,Locale.getDefault());
        List<Address> addresses;
        try{
            addresses = geocoder.getFromLocation(lat,lon,10);
            cityName =  addresses.get(0).getLocality();
        } catch (IOException e){
            e.printStackTrace();
        }
        return cityName;
    }

    //this function is called when all permissions are granted
    //this function will show the GPS information.
    private void updateShow(Location location, String city) {
        if (location != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Current Location Information:\n");
            sb.append("Longitude：" + location.getLongitude() + "\n");
            sb.append("Latitude：" + location.getLatitude() + "\n");
            sb.append("Altitude：" + location.getAltitude() + "\n");
            sb.append("Speed：" + location.getSpeed() + "\n");
            sb.append("Bearing：" + location.getBearing() + "\n");
            sb.append("Accuracy：" + location.getAccuracy() + "\n");
            sb.append("City：" + city + "\n");
            textView.setText(sb.toString());
        } else textView.setText("");
    }

    //to send GPS location and user information
    private void sendGPSLocation(Location location, ServerConnection connection, String action) {
        if (location != null) {
            try
            {
                //then call this method to send
                connection.SendGPSbyPost(((Double)location.getLongitude()).toString(),((Double)location.getLatitude()).toString(), action);
            }catch (Exception e){
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this,"Send Request Failed! No location found!", Toast.LENGTH_SHORT).show();
        }
    }

    //this is the implementation of the interface ServerConnection.onResponseReadyListener
    //when the response message from server is ready, this function will be called
    public void onResponseReady(String msg)
    {
        ShowToast(msg);
    }

    private void ShowToast(final String msg)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT).show();
            }
        });
    }
}
