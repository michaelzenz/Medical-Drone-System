package com.airlife.control;

import android.os.Handler;
import android.os.Looper;

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

public class AutoController {

    private FlightController mFlightController;
    private Timer mSendDataTimer;
    private SendDataTask mSendDataTask;
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;
    public String Err_msg;


    public AutoController()
    {
       initFlightController();
    }
    /**
     * Call this function to update the flight index pX and pY.
     * @param pX
     * @param pY
     */
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
                        }
                    }
            );

        }
    }


    /**
     * Initialize the FlightController
     */

    public void initFlightController() {

        Aircraft aircraft = DJIDemoApplication.getAircraftInstance();
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
    }

    public String EnableAutoControl()
    {
        if (mFlightController != null){

            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null){
                        Err_msg = djiError.getDescription();//djiError.getDescription()
                    }else
                    {
                        Err_msg = "Enable Virtual Stick Success";
                    }
                }
            });
        }
        return Err_msg;
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
