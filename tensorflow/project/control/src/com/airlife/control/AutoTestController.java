package com.airlife.control;

import android.os.Handler;
import android.os.Looper;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;

public class AutoTestController implements ServerConnection.onResponseReadyListener{

    private static ServerConnection connection=null;
    private TrackingTestActivity mMissionRunner=null;

    private FlightController mFlightController;
    private Timer mSendDataTimer;
    private SendDataTask mSendDataTask;
    private Timer mSendServerTimer;
    private SendServerTask mSendServerTask;
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;
    public String Err_msg;

    private double droneLongitude=1.0;
    private double droneLatitude=1.0;

    class SendServerTask extends TimerTask {
        @Override
        public void run() {
            sendGPSLocation(droneLongitude, droneLatitude, "Answer");
        }
    }

    public AutoTestController(TrackingTestActivity missionRunner)
    {
        initFlightController();
        mMissionRunner=missionRunner;
        if(connection==null)connection = new ServerConnection(this);
    }

    /**
     * Original Codes, don't modify unless necessary
     */
    private void sendGPSLocation(Double Longitude, Double Latitude, String Action) {
        if (connection != null) {
            try {
                //then call this method to send
                connection.SendGPSbyPost(Longitude.toString(), Latitude.toString(), Action);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            mMissionRunner.showToast("No Server Connection");
        }
    }

    //this is the implementation of the interface ServerConnection.onResponseReadyListener
    //when the response message from server is ready, this function will be called
    public void onResponseReady(String msg) {
        JSONParser parser = new JSONParser();
        JSONObject obj;
        try {
            obj = (JSONObject) parser.parse(msg);
            String Action = (String) obj.get("Action");
            if (Action.equals("Request")) {
                TakeOff();
                mMissionRunner.showToast("Request received, Taking Off");
                Delay(5000);
                EnableAutoControl();
                UpdateData(0.0f,0.0f);
            } else if (Action.equals("Cancel")) {
                UpdateData(0.0f,0.0f);
                Landing();
                mMissionRunner.showToast("Request canceled, landing");
            } else {
                mMissionRunner.showToast("Unknown Action");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // For Delay after take off to initialize the virtual stick controller. May cause error without the delay.
    private void Delay(int delay) {
        try {
            Thread.currentThread();
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void UpdateGPS(double longitude, double latitude){
        droneLongitude=longitude;
        droneLatitude=latitude;
    }

    public void UpdateData(float pX, float pY)
    {

        if(Math.abs(pX) < 0.02 ){
            pX = 0;
        }

        if(Math.abs(pY) < 0.02 ){
            pY = 0;
        }
        float pitchJoyControlMaxSpeed = 10;
        float rollJoyControlMaxSpeed = 10;
        float verticalJoyControlMaxSpeed = 2;
        float yawJoyControlMaxSpeed = 30;

        mYaw = (float)(yawJoyControlMaxSpeed * pX);
        mThrottle = (float)(verticalJoyControlMaxSpeed * pY);
        mPitch = (float)(pitchJoyControlMaxSpeed * pX);
        mRoll = (float)(rollJoyControlMaxSpeed * pY);
        CreateTimer();
    }

    /**
     * Call this function to get the FlightController used in the AutoController.
     * @return
     */
    public FlightController getAutoController()
    {
        return mFlightController;
    }

    /**
     * Call this function to take-off. The drone take off automatically when the controller's builder function is called.
     */
    public void TakeOff()
    {
        if (mFlightController != null){
            mFlightController.startTakeoff(
                    new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {

                        }
                    }
            );
        }
    }

    /**
     * Call this function to land
     */
    public void Landing()
    {
        if (mFlightController != null){

            mFlightController.startLanding(
                    new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            EnableAutoControl();
                        }
                    }
            );

        }
    }

    /**
     * Initialize the FlightController
     */

    public void initFlightController() {

        Aircraft aircraft = BaseActivity.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            mFlightController.getSimulator().setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(final SimulatorState stateData) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {

                        }
                    });
                }
            });
        }
    }

    /**
     * Create Timer and Send data if a timer does not exist.
     */
    public void CreateTimer()
    {
        if (null == mSendDataTimer) {
            mSendDataTask = new SendDataTask();
            mSendDataTimer = new Timer();
            mSendDataTimer.schedule(mSendDataTask, 100, 200);
        }
        if (null == mSendServerTimer) {
            mSendServerTask = new SendServerTask();
            mSendServerTimer = new Timer();
            mSendServerTimer.schedule(mSendServerTask, 1000, 2500);
        }
    }

    public void EnableAutoControl()
    {
        if (mFlightController != null){

            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null){
                        mMissionRunner.showToast(djiError.getDescription());
                    }else
                    {
                        mMissionRunner.showToast("Virtual Stick Enabled");
                    }
                }
            });
        }
    }

    class SendDataTask extends TimerTask {

        @Override
        public void run() {
            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                            }
                        }
                );
            }
        }
    }


}
