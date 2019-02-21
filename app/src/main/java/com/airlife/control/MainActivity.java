package com.airlife.control;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SlidingDrawer;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.mission.activetrack.ActiveTrackMission;
import dji.common.mission.activetrack.ActiveTrackMissionEvent;
import dji.common.mission.activetrack.ActiveTrackMode;
import dji.common.mission.activetrack.ActiveTrackState;
import dji.common.mission.activetrack.ActiveTrackTargetState;
import dji.common.mission.activetrack.ActiveTrackTrackingState;
import dji.common.mission.activetrack.QuickShotMode;
import dji.common.mission.activetrack.SubjectSensingState;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.keysdk.CameraKey;
import dji.keysdk.DJIKey;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.keysdk.callback.ActionCallback;
import dji.keysdk.callback.SetCallback;
import dji.log.DJILog;
import dji.midware.media.DJIVideoDataRecver;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.activetrack.ActiveTrackMissionOperatorListener;
import dji.sdk.mission.activetrack.ActiveTrackOperator;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import dji.thirdparty.afinal.core.AsyncTask;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class MainActivity extends DemoBaseActivity implements TextureView.SurfaceTextureListener, View.OnClickListener, ServerConnection.onResponseReadyListener,View.OnTouchListener, CompoundButton.OnCheckedChangeListener, ActiveTrackMissionOperatorListener {

    private static final String TAG = MainActivity.class.getName();
    private ServerConnection connection;
    protected TextView mConnectStatusTextView;
    private AutoController controller;
    private TextView mTextView;
    private Button btnStart;
    private Button btnEnd;
    private Button btnTest;
    private Timer mSendServerTimer;
    private SendServerTask mSendServerTask;

    private static final int MAIN_CAMERA_INDEX = 0;
    private static final int INVAVID_INDEX = -1;
    private static final int MOVE_OFFSET = 20;

    private RelativeLayout.LayoutParams layoutParams;
    private Switch mAutoSensingSw;
    private Switch mQuickShotSw;
    private ImageButton mPushDrawerIb;
    private SlidingDrawer mPushInfoSd;
    private ImageButton mStopBtn;
    private ImageView mTrackingImage;
    private RelativeLayout mBgLayout;
    private TextView mPushInfoTv;
    private Switch mPushBackSw;
    private Switch mGestureModeSw;
    private ImageView mSendRectIV;
    private Button mConfigBtn;
    private Button mConfirmBtn;
    private Button mRejectBtn;

    private ActiveTrackOperator mActiveTrackOperator;
    private ActiveTrackMission mActiveTrackMission;
    private final DJIKey trackModeKey = FlightControllerKey.createFlightAssistantKey(FlightControllerKey.ACTIVE_TRACK_MODE);
    private int trackingIndex = INVAVID_INDEX;
    private boolean isAutoSensingSupported = false;
    private ConcurrentHashMap<Integer, MultiTrackingView> targetViewHashMap = new ConcurrentHashMap<>();
    private ActiveTrackMode startMode = ActiveTrackMode.TRACE;
    private QuickShotMode quickShotMode = QuickShotMode.UNKNOWN;

    private boolean isDrawingRect = false;

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    private void setResultToToast(final String string) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Push Status to TextView
     *
     * @param string
     */
    private void setResultToText(final String string) {
        if (mPushInfoTv == null) {
            setResultToToast("Push info tv has not be init...");
        }
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPushInfoTv.setText(string);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {




        // When the compile and target version is higher than 22, please request the following permission at runtime to ensure the SDK works well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        }
        setContentView(R.layout.activity_main);
        super.onCreate(savedInstanceState);
/*        //Initialize DJI SDK Manager
        mHandler = new Handler(Looper.getMainLooper());*/
        connection = new ServerConnection(this);
        initUI();
        CreateTimer();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        registerReceiver(mReceiver, filter);
    }


    public void CreateTimer() {
        if (null == mSendServerTimer) {
            mSendServerTask = new SendServerTask();
            mSendServerTimer = new Timer();
            mSendServerTimer.schedule(mSendServerTask, 1000, 2500);
        }
    }


    class SendServerTask extends TimerTask {

        @Override
        public void run() {
            sendGPSLocation(1.0, 1.0, "Answer");
        }
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            showToast("Need to grant the permissions!");
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(MainActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                showToast("Register Success");
                                DJISDKManager.getInstance().startConnectionToProduct();
                            } else {
                                showToast("Register sdk fails, please check the bundle id and network connection!");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            showToast("Product Disconnected");

                        }

                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            showToast("Product Connected");

                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
                                    }
                                });
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));

                        }
                    });
                }
            });
        }
    }


    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }



    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initMissionManager();
        CreateTimer();
        if (null != controller) {
            controller.initFlightController();
        }
    }

    /**
     * Init Mission parameter
     */
    private void initMissionManager() {
        mActiveTrackOperator = MissionControl.getInstance().getActiveTrackOperator();
        if (mActiveTrackOperator == null) {
            return;
        }

        mActiveTrackOperator.addListener(this);
        mAutoSensingSw.setChecked(mActiveTrackOperator.isAutoSensingEnabled());
        mQuickShotSw.setChecked(mActiveTrackOperator.isAutoSensingForQuickShotEnabled());
        mGestureModeSw.setChecked(mActiveTrackOperator.isGestureModeEnabled());
        mActiveTrackOperator.getRetreatEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(final Boolean aBoolean) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPushBackSw.setChecked(aBoolean);
                    }
                });
            }

            @Override
            public void onFailure(DJIError error) {
                setResultToToast("can't get retreat enable state " + error.getDescription());
            }
        });
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view) {
        DJILog.d(TAG, "onReturn");
        DJISDKManager.getInstance().getMissionControl().destroy();
        this.finish();
    }


    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        if (null != mSendServerTimer) {
            mSendServerTask.cancel();
            mSendServerTask = null;
            mSendServerTimer.cancel();
            mSendServerTimer.purge();
            mSendServerTimer = null;
        }
        isAutoSensingSupported = false;
        try {
            DJIVideoDataRecver.getInstance().setVideoDataListener(false, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mActiveTrackOperator != null) {
            mActiveTrackOperator.removeListener(this);
        }

        if (mCodecManager != null) {
            mCodecManager.destroyCodec();
        }

        super.onDestroy();
    }

    float downX;
    float downY;

    /**
     * Calculate distance
     *
     * @param point1X
     * @param point1Y
     * @param point2X
     * @param point2Y
     * @return
     */
    private double calcManhattanDistance(double point1X, double point1Y, double point2X,
                                         double point2Y) {
        return Math.abs(point1X - point2X) + Math.abs(point1Y - point2Y);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDrawingRect = false;
                downX = event.getX();
                downY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (calcManhattanDistance(downX, downY, event.getX(), event.getY()) < MOVE_OFFSET && !isDrawingRect) {
                    trackingIndex = getTrackingIndex(downX, downY, targetViewHashMap);
                    if (targetViewHashMap.get(trackingIndex) != null) {
                        targetViewHashMap.get(trackingIndex).setBackgroundColor(Color.RED);
                    }
                    return true;
                }
                isDrawingRect = true;
                mSendRectIV.setVisibility(View.VISIBLE);
                int l = (int) (downX < event.getX() ? downX : event.getX());
                int t = (int) (downY < event.getY() ? downY : event.getY());
                int r = (int) (downX >= event.getX() ? downX : event.getX());
                int b = (int) (downY >= event.getY() ? downY : event.getY());
                mSendRectIV.setX(l);
                mSendRectIV.setY(t);
                mSendRectIV.getLayoutParams().width = r - l;
                mSendRectIV.getLayoutParams().height = b - t;
                mSendRectIV.requestLayout();
                break;

            case MotionEvent.ACTION_UP:
                if (mGestureModeSw.isChecked()) {
                    setResultToToast("Please try to start Gesture Mode!");
                } else if (!isDrawingRect) {
                    if (targetViewHashMap.get(trackingIndex) != null) {
                        setResultToToast("Selected Index: " + trackingIndex + ",Please Confirm it!");
                        targetViewHashMap.get(trackingIndex).setBackgroundColor(Color.TRANSPARENT);
                    }
                } else {
                    RectF rectF = getActiveTrackRect(mSendRectIV);
                    mActiveTrackMission = new ActiveTrackMission(rectF, startMode);
                    if (startMode == ActiveTrackMode.QUICK_SHOT) {
                        mActiveTrackMission.setQuickShotMode(quickShotMode);
                        checkStorageStates();
                    }
                    mActiveTrackOperator.startTracking(mActiveTrackMission, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {
                            if (error == null) {
                                isDrawingRect = false;
                            }
                            setResultToToast("Start Tracking: " + (error == null
                                    ? "Success"
                                    : error.getDescription()));
                        }
                    });
                    mSendRectIV.setVisibility(View.INVISIBLE);
                    clearCurrentView();
                }
                break;

            default:
                break;
        }

        return true;
    }

    /**
     * @return
     */
    private int getTrackingIndex(final float x, final float y,
                                 final ConcurrentHashMap<Integer, MultiTrackingView> multiTrackinghMap) {
        if (multiTrackinghMap == null || multiTrackinghMap.isEmpty()) {
            return INVAVID_INDEX;
        }

        float l, t, r, b;
        for (Map.Entry<Integer, MultiTrackingView> vo : multiTrackinghMap.entrySet()) {
            int key = vo.getKey().intValue();
            MultiTrackingView view = vo.getValue();
            l = view.getX();
            t = view.getY();
            r = (view.getX() + (view.getWidth() / 2));
            b = (view.getY() + (view.getHeight() / 2));

            if (x >= l && y >= t && x <= r && y <= b) {
                return key;
            }
        }
        return INVAVID_INDEX;
    }

    @Override
    public void onUpdate(ActiveTrackMissionEvent event) {
        StringBuffer sb = new StringBuffer();
        String errorInformation = (event.getError() == null ? "null" : event.getError().getDescription()) + "\n";
        String currentState = event.getCurrentState() == null ? "null" : event.getCurrentState().getName();
        String previousState = event.getPreviousState() == null ? "null" : event.getPreviousState().getName();

        ActiveTrackTargetState targetState = ActiveTrackTargetState.UNKNOWN;
        if (event.getTrackingState() != null) {
            targetState = event.getTrackingState().getState();
        }
        Utils.addLineToSB(sb, "CurrentState: ", currentState);
        Utils.addLineToSB(sb, "PreviousState: ", previousState);
        Utils.addLineToSB(sb, "TargetState: ", targetState);
        Utils.addLineToSB(sb, "Error:", errorInformation);

        Object value = KeyManager.getInstance().getValue(trackModeKey);
        if (value instanceof ActiveTrackMode) {
            Utils.addLineToSB(sb, "TrackingMode:", value.toString());
        }

        ActiveTrackTrackingState trackingState = event.getTrackingState();
        if (trackingState != null) {
            final SubjectSensingState[] targetSensingInformations = trackingState.getAutoSensedSubjects();
            if (targetSensingInformations != null) {
                for (SubjectSensingState subjectSensingState : targetSensingInformations) {
                    RectF trackingRect = subjectSensingState.getTargetRect();
                    if (trackingRect != null) {
                        Utils.addLineToSB(sb, "Rect center x: ", trackingRect.centerX());
                        Utils.addLineToSB(sb, "Rect center y: ", trackingRect.centerY());
                        Utils.addLineToSB(sb, "Rect Width: ", trackingRect.width());
                        Utils.addLineToSB(sb, "Rect Height: ", trackingRect.height());
                        Utils.addLineToSB(sb, "Reason", trackingState.getReason().name());
                        Utils.addLineToSB(sb, "Target Index: ", subjectSensingState.getIndex());
                        Utils.addLineToSB(sb, "Target Type", subjectSensingState.getTargetType().name());
                        Utils.addLineToSB(sb, "Target State", subjectSensingState.getState().name());
                        isAutoSensingSupported = true;
                    }
                }
            } else {
                RectF trackingRect = trackingState.getTargetRect();
                if (trackingRect != null) {
                    Utils.addLineToSB(sb, "Rect center x: ", trackingRect.centerX());
                    Utils.addLineToSB(sb, "Rect center y: ", trackingRect.centerY());
                    Utils.addLineToSB(sb, "Rect Width: ", trackingRect.width());
                    Utils.addLineToSB(sb, "Rect Height: ", trackingRect.height());
                    Utils.addLineToSB(sb, "Reason", trackingState.getReason().name());
                    Utils.addLineToSB(sb, "Target Index: ", trackingState.getTargetIndex());
                    Utils.addLineToSB(sb, "Target Type", trackingState.getType().name());
                    Utils.addLineToSB(sb, "Target State", trackingState.getState().name());
                    isAutoSensingSupported = false;
                }
                clearCurrentView();
            }
        }

        setResultToText(sb.toString());
        updateActiveTrackRect(mTrackingImage, event);
        updateButtonVisibility(event);
    }



    private void initUI() {
        layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        mPushDrawerIb = (ImageButton) findViewById(R.id.tracking_drawer_control_ib);
        mPushInfoSd = (SlidingDrawer) findViewById(R.id.tracking_drawer_sd);
        mPushInfoTv = (TextView) findViewById(R.id.tracking_push_tv);
        mBgLayout = (RelativeLayout) findViewById(R.id.tracking_bg_layout);
        mSendRectIV = (ImageView) findViewById(R.id.tracking_send_rect_iv);
        mTrackingImage = (ImageView) findViewById(R.id.tracking_rst_rect_iv);
        mConfirmBtn = (Button) findViewById(R.id.confirm_btn);
        mStopBtn = (ImageButton) findViewById(R.id.tracking_stop_btn);
        mRejectBtn = (Button) findViewById(R.id.reject_btn);
        mConfigBtn = (Button) findViewById(R.id.recommended_configuration_btn);
        mAutoSensingSw = (Switch) findViewById(R.id.set_multitracking_enabled);
        mQuickShotSw = (Switch) findViewById(R.id.set_multiquickshot_enabled);
        mPushBackSw = (Switch) findViewById(R.id.tracking_pull_back_tb);
        mGestureModeSw = (Switch) findViewById(R.id.tracking_in_gesture_mode);



        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        btnEnd = (Button) findViewById(R.id.end);
        btnStart = (Button) findViewById(R.id.start);
        btnTest = (Button) findViewById(R.id.test);
        btnTest.setOnClickListener(this);
        btnEnd.setOnClickListener(this);
        btnStart.setOnClickListener(this);



        mAutoSensingSw.setChecked(false);
        mGestureModeSw.setChecked(false);
        mQuickShotSw.setChecked(false);
        mPushBackSw.setChecked(false);

        mAutoSensingSw.setOnCheckedChangeListener(this);
        mQuickShotSw.setOnCheckedChangeListener(this);
        mPushBackSw.setOnCheckedChangeListener(this);
        mGestureModeSw.setOnCheckedChangeListener(this);

        mBgLayout.setOnTouchListener(this);
        mConfirmBtn.setOnClickListener(this);
        mStopBtn.setOnClickListener(this);
        mRejectBtn.setOnClickListener(this);
        mConfigBtn.setOnClickListener(this);
        mPushDrawerIb.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

        if (mActiveTrackOperator == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.recommended_configuration_btn:
                mActiveTrackOperator.setRecommendedConfiguration(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                        setResultToToast("Set Recommended Config " + (error == null ? "Success" : error.getDescription()));
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConfigBtn.setVisibility(View.GONE);
                    }
                });
                break;

            case R.id.confirm_btn:
                boolean isAutoTracking =
                        isAutoSensingSupported &&
                                (mActiveTrackOperator.isAutoSensingEnabled() ||
                                        mActiveTrackOperator.isAutoSensingForQuickShotEnabled());
                if (isAutoTracking) {
                    startAutoSensingMission();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mStopBtn.setVisibility(View.VISIBLE);
                            mRejectBtn.setVisibility(View.VISIBLE);
                            mConfirmBtn.setVisibility(View.INVISIBLE);
                        }
                    });
                } else {
                    trackingIndex = INVAVID_INDEX;
                    mActiveTrackOperator.acceptConfirmation(new CommonCallbacks.CompletionCallback() {

                        @Override
                        public void onResult(DJIError error) {
                            setResultToToast(error == null ? "Accept Confirm Success!" : error.getDescription());
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mStopBtn.setVisibility(View.VISIBLE);
                            mRejectBtn.setVisibility(View.VISIBLE);
                            mConfirmBtn.setVisibility(View.INVISIBLE);
                        }
                    });

                }
                break;

            case R.id.tracking_stop_btn:
                trackingIndex = INVAVID_INDEX;
                mActiveTrackOperator.stopTracking(new CommonCallbacks.CompletionCallback() {

                    @Override
                    public void onResult(DJIError error) {
                        setResultToToast(error == null ? "Stop track Success!" : error.getDescription());
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTrackingImage != null) {
                            mTrackingImage.setVisibility(View.INVISIBLE);
                            mSendRectIV.setVisibility(View.INVISIBLE);
                            mStopBtn.setVisibility(View.INVISIBLE);
                            mRejectBtn.setVisibility(View.INVISIBLE);
                            mConfirmBtn.setVisibility(View.VISIBLE);
                        }
                    }
                });
                break;

            case R.id.reject_btn:
                trackingIndex = INVAVID_INDEX;
                mActiveTrackOperator.rejectConfirmation(new CommonCallbacks.CompletionCallback() {

                    @Override
                    public void onResult(DJIError error) {

                        setResultToToast(error == null ? "Reject Confirm Success!" : error.getDescription());
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStopBtn.setVisibility(View.VISIBLE);
                        mRejectBtn.setVisibility(View.VISIBLE);
                        mConfirmBtn.setVisibility(View.INVISIBLE);
                    }
                });
                break;

            case R.id.tracking_drawer_control_ib:
                if (mPushInfoSd.isOpened()) {
                    mPushInfoSd.animateClose();
                } else {
                    mPushInfoSd.animateOpen();
                }
                break;

            case R.id.end:
                if (null != controller) {
                    controller.Landing();
                    controller = null;
                } else {
                    showToast("Currently No Controller");
                }
                break;

            case R.id.start:
                if (null == controller) {
                    controller = new AutoController();
                    controller.initFlightController();
                    controller.TakeOff();
                    showToast("Take Off Success");
                } else {
                    controller.initFlightController();
                    controller.TakeOff();
                    showToast("Take Off Success");
                }
                break;
            case R.id.test:
                if (null != controller) {
                    showToast("Testing!");
                    controller.EnableAutoControl();
                    showToast(controller.Err_msg);
                    controller.UpdateData(0.5f, 0.5f);
                } else {
                    showToast("Currently No Controller");
                }
                break;

            default:
                break;
        }


    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, final boolean isChecked) {
        if (mActiveTrackOperator == null) {
            return;
        }
        switch (compoundButton.getId()) {
            case R.id.set_multitracking_enabled:
                startMode = ActiveTrackMode.TRACE;
                quickShotMode = QuickShotMode.UNKNOWN;
                setAutoSensingEnabled(isChecked);
                break;
            case R.id.set_multiquickshot_enabled:
                startMode = ActiveTrackMode.QUICK_SHOT;
                quickShotMode = QuickShotMode.CIRCLE;
                checkStorageStates();
                setAutoSensingForQuickShotEnabled(isChecked);
                break;
            case R.id.tracking_pull_back_tb:
                mActiveTrackOperator.setRetreatEnabled(isChecked, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                        if (error != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mPushBackSw.setChecked(!isChecked);
                                }
                            });
                        }
                        setResultToToast("Set Retreat Enabled: " + (error == null
                                ? "Success"
                                : error.getDescription()));
                    }
                });
                break;
            case R.id.tracking_in_gesture_mode:
                mActiveTrackOperator.setGestureModeEnabled(isChecked, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                        if (error != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mGestureModeSw.setChecked(!isChecked);
                                }
                            });
                        }
                        setResultToToast("Set GestureMode Enabled: " + (error == null
                                ? "Success"
                                : error.getDescription()));
                    }
                });
                break;
            default:
                break;
        }
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
            showToast("No Server Connection");
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
                if (null == controller) {
                    controller = new AutoController();
                    controller.initFlightController();
                    controller.TakeOff();
                    showToast("Request received, Taking Off");
                } else {
                    showToast("No Controller");
                }
                Delay(5000);
                controller.EnableAutoControl();
                controller.UpdateData(0.0f,0.0f);
            } else if (Action.equals("Cancel")) {
                if (null != controller) {
                    controller.UpdateData(0.0f,0.0f);
                    controller.Landing();
                    controller = null;
                }
                showToast("Request canceled, landing");
            } else {
                showToast("No Controller");
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
/**
 * Ends here
*/
    /**
     * Update ActiveTrack Rect
     *
     * @param iv
     * @param event
     */
    private void updateActiveTrackRect(final ImageView iv, final ActiveTrackMissionEvent event) {
        if (iv == null || event == null) {
            return;
        }

        ActiveTrackTrackingState trackingState = event.getTrackingState();
        if (trackingState != null) {
            if (trackingState.getAutoSensedSubjects() != null) {
                final SubjectSensingState[] targetSensingInformations = trackingState.getAutoSensedSubjects();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateMultiTrackingView(targetSensingInformations);
                    }
                });
            } else {
                RectF trackingRect = trackingState.getTargetRect();
                ActiveTrackTargetState trackTargetState = trackingState.getState();
                postResultRect(iv, trackingRect, trackTargetState);
            }
        }

    }

    private void updateButtonVisibility(final ActiveTrackMissionEvent event) {
        ActiveTrackState state = event.getCurrentState();
        if (state == ActiveTrackState.AUTO_SENSING ||
                state == ActiveTrackState.AUTO_SENSING_FOR_QUICK_SHOT ||
                state == ActiveTrackState.WAITING_FOR_CONFIRMATION) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStopBtn.setVisibility(View.VISIBLE);
                    mStopBtn.setClickable(true);
                    mConfirmBtn.setVisibility(View.VISIBLE);
                    mConfirmBtn.setClickable(true);
                    mRejectBtn.setVisibility(View.VISIBLE);
                    mRejectBtn.setClickable(true);
                    mConfigBtn.setVisibility(View.GONE);
                }
            });
        } else if (state == ActiveTrackState.AIRCRAFT_FOLLOWING ||
                state == ActiveTrackState.ONLY_CAMERA_FOLLOWING ||
                state == ActiveTrackState.FINDING_TRACKED_TARGET ||
                state == ActiveTrackState.CANNOT_CONFIRM ||
                state == ActiveTrackState.PERFORMING_QUICK_SHOT) {
            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mStopBtn.setVisibility(View.VISIBLE);
                    mStopBtn.setClickable(true);
                    mConfirmBtn.setVisibility(View.INVISIBLE);
                    mConfirmBtn.setClickable(false);
                    mRejectBtn.setVisibility(View.VISIBLE);
                    mRejectBtn.setClickable(true);
                    mConfigBtn.setVisibility(View.GONE);
                }
            });
        } else {
            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mStopBtn.setVisibility(View.INVISIBLE);
                    mStopBtn.setClickable(false);
                    mConfirmBtn.setVisibility(View.INVISIBLE);
                    mConfirmBtn.setClickable(false);
                    mRejectBtn.setVisibility(View.INVISIBLE);
                    mRejectBtn.setClickable(false);
                    mTrackingImage.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    /**
     * Get ActiveTrack RectF
     *
     * @param iv
     * @return
     */
    private RectF getActiveTrackRect(View iv) {
        View parent = (View) iv.getParent();
        return new RectF(
                ((float) iv.getLeft() + iv.getX()) / (float) parent.getWidth(),
                ((float) iv.getTop() + iv.getY()) / (float) parent.getHeight(),
                ((float) iv.getRight() + iv.getX()) / (float) parent.getWidth(),
                ((float) iv.getBottom() + iv.getY()) / (float) parent.getHeight());
    }

    /**
     * Post Result RectF
     *
     * @param iv
     * @param rectF
     * @param targetState
     */
    private void postResultRect(final ImageView iv, final RectF rectF,
                                final ActiveTrackTargetState targetState) {
        View parent = (View) iv.getParent();
        RectF trackingRect = rectF;

        final int l = (int) ((trackingRect.centerX() - trackingRect.width() / 2) * parent.getWidth());
        final int t = (int) ((trackingRect.centerY() - trackingRect.height() / 2) * parent.getHeight());
        final int r = (int) ((trackingRect.centerX() + trackingRect.width() / 2) * parent.getWidth());
        final int b = (int) ((trackingRect.centerY() + trackingRect.height() / 2) * parent.getHeight());

        MainActivity.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mTrackingImage.setVisibility(View.VISIBLE);
                if ((targetState == ActiveTrackTargetState.CANNOT_CONFIRM)
                        || (targetState == ActiveTrackTargetState.UNKNOWN)) {
                    iv.setImageResource(R.drawable.visual_track_cannotconfirm);
                } else if (targetState == ActiveTrackTargetState.WAITING_FOR_CONFIRMATION) {
                    iv.setImageResource(R.drawable.visual_track_needconfirm);
                } else if (targetState == ActiveTrackTargetState.TRACKING_WITH_LOW_CONFIDENCE) {
                    iv.setImageResource(R.drawable.visual_track_lowconfidence);
                } else if (targetState == ActiveTrackTargetState.TRACKING_WITH_HIGH_CONFIDENCE) {
                    iv.setImageResource(R.drawable.visual_track_highconfidence);
                }
                iv.setX(l);
                iv.setY(t);
                iv.getLayoutParams().width = r - l;
                iv.getLayoutParams().height = b - t;
                iv.requestLayout();
            }
        });
    }

    /**
     * PostMultiResult
     *
     * @param iv
     * @param rectF
     * @param information
     */
    private void postMultiResultRect(final MultiTrackingView iv, final RectF rectF,
                                     final SubjectSensingState information) {
        View parent = (View) iv.getParent();
        RectF trackingRect = rectF;

        final int l = (int) ((trackingRect.centerX() - trackingRect.width() / 2) * parent.getWidth());
        final int t = (int) ((trackingRect.centerY() - trackingRect.height() / 2) * parent.getHeight());
        final int r = (int) ((trackingRect.centerX() + trackingRect.width() / 2) * parent.getWidth());
        final int b = (int) ((trackingRect.centerY() + trackingRect.height() / 2) * parent.getHeight());

        MainActivity.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mTrackingImage.setVisibility(View.INVISIBLE);
                iv.setX(l);
                iv.setY(t);
                iv.getLayoutParams().width = r - l;
                iv.getLayoutParams().height = b - t;
                iv.requestLayout();
                iv.updateView(information);
            }
        });
    }

    /**
     * Update MultiTrackingView
     *
     * @param targetSensingInformations
     */
    private void updateMultiTrackingView(final SubjectSensingState[] targetSensingInformations) {
        ArrayList<Integer> indexs = new ArrayList<>();
        for (SubjectSensingState target : targetSensingInformations) {
            indexs.add(target.getIndex());
            if (targetViewHashMap.containsKey(target.getIndex())) {

                MultiTrackingView targetView = targetViewHashMap.get(target.getIndex());
                postMultiResultRect(targetView, target.getTargetRect(), target);
            } else {
                MultiTrackingView trackingView = new MultiTrackingView(MainActivity.this);
                mBgLayout.addView(trackingView, layoutParams);
                targetViewHashMap.put(target.getIndex(), trackingView);
            }
        }

        ArrayList<Integer> missingIndexs = new ArrayList<>();
        for (Integer key : targetViewHashMap.keySet()) {
            boolean isDisappeared = true;
            for (Integer index : indexs) {
                if (index.equals(key)) {
                    isDisappeared = false;
                    break;
                }
            }

            if (isDisappeared) {
                missingIndexs.add(key);
            }
        }

        for (Integer i : missingIndexs) {
            MultiTrackingView view = targetViewHashMap.remove(i);
            mBgLayout.removeView(view);
        }
    }


    /**
     * Enable MultiTracking
     *
     * @param isChecked
     */
    private void setAutoSensingEnabled(final boolean isChecked) {
        if (mActiveTrackOperator != null) {
            if (isChecked) {
                startMode = ActiveTrackMode.TRACE;
                mActiveTrackOperator.enableAutoSensing(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                        if (error != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mAutoSensingSw.setChecked(!isChecked);
                                }
                            });
                        }
                        setResultToToast("Set AutoSensing Enabled " + (error == null ? "Success!" : error.getDescription()));
                    }
                });
            } else {
                disableAutoSensing();
            }
        }
    }

    /**
     * Enable QuickShotMode
     *
     * @param isChecked
     */
    private void setAutoSensingForQuickShotEnabled(final boolean isChecked) {
        if (mActiveTrackOperator != null) {
            if (isChecked) {
                mActiveTrackOperator.enableAutoSensingForQuickShot(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                        if (error != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mQuickShotSw.setChecked(!isChecked);
                                }
                            });
                        }
                        setResultToToast("Set QuickShot Enabled " + (error == null ? "Success!" : error.getDescription()));
                    }
                });

            } else {
                disableAutoSensing();
            }

        }
    }

    /**
     * Disable AutoSensing
     */
    private void disableAutoSensing() {
        if (mActiveTrackOperator != null) {
            mActiveTrackOperator.disableAutoSensing(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    if (error == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mConfirmBtn.setVisibility(View.INVISIBLE);
                                mStopBtn.setVisibility(View.INVISIBLE);
                                mRejectBtn.setVisibility(View.INVISIBLE);
                                mConfigBtn.setVisibility(View.VISIBLE);
                                isAutoSensingSupported = false;
                            }
                        });
                        clearCurrentView();
                    }
                    setResultToToast(error == null ? "Disable Auto Sensing Success!" : error.getDescription());
                }
            });
        }
    }


    /**
     * Confim Mission by Index
     */
    private void startAutoSensingMission() {
        if (trackingIndex != INVAVID_INDEX) {
            ActiveTrackMission mission = new ActiveTrackMission(null, startMode);
            mission.setQuickShotMode(quickShotMode);
            mission.setTargetIndex(trackingIndex);
            mActiveTrackOperator.startAutoSensingMission(mission, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    if (error == null) {
                        setResultToToast("Accept Confim index: " + trackingIndex + " Success!");
                        trackingIndex = INVAVID_INDEX;
                    } else {
                        setResultToToast(error.getDescription());
                    }
                }
            });
        }
    }


    /**
     * Change Storage Location
     */
    private void switchStorageLocation(final SettingsDefinitions.StorageLocation storageLocation) {
        KeyManager keyManager = KeyManager.getInstance();
        DJIKey storageLoactionkey = CameraKey.create(CameraKey.CAMERA_STORAGE_LOCATION, MAIN_CAMERA_INDEX);

        if (storageLocation == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) {
            keyManager.setValue(storageLoactionkey, SettingsDefinitions.StorageLocation.SDCARD, new SetCallback() {
                @Override
                public void onSuccess() {
                    setResultToToast("Change to SD card Success!");
                }

                @Override
                public void onFailure(@NonNull DJIError error) {
                    setResultToToast(error.getDescription());
                }
            });
        } else {
            keyManager.setValue(storageLoactionkey, SettingsDefinitions.StorageLocation.INTERNAL_STORAGE, new SetCallback() {
                @Override
                public void onSuccess() {
                    setResultToToast("Change to Interal Storage Success!");
                }

                @Override
                public void onFailure(@NonNull DJIError error) {
                    setResultToToast(error.getDescription());
                }
            });
        }
    }

    /**
     * determine SD Card is or not Ready
     *
     * @param index
     * @return
     */
    private boolean isSDCardReady(int index) {
        KeyManager keyManager = KeyManager.getInstance();

        return ((Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_INSERTED, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_INITIALIZING, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_READ_ONLY, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_HAS_ERROR, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_FULL, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_BUSY, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_FORMATTING, index))
                && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_INVALID_FORMAT, index))
                && (Boolean) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_IS_VERIFIED, index))
                && (Long) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_AVAILABLE_CAPTURE_COUNT, index)) > 0L
                && (Integer) keyManager.getValue(CameraKey.create(CameraKey.SDCARD_AVAILABLE_RECORDING_TIME_IN_SECONDS, index)) > 0);
    }

    /**
     * determine Interal Storage is or not Ready
     *
     * @param index
     * @return
     */
    private boolean isInteralStorageReady(int index) {
        KeyManager keyManager = KeyManager.getInstance();

        boolean isInternalSupported = (boolean)
                keyManager.getValue(CameraKey.create(CameraKey.IS_INTERNAL_STORAGE_SUPPORTED, index));
        if (isInternalSupported) {
            return ((Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_INSERTED, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_INITIALIZING, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_READ_ONLY, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_HAS_ERROR, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_FULL, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_BUSY, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_FORMATTING, index))
                    && !(Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_INVALID_FORMAT, index))
                    && (Boolean) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_IS_VERIFIED, index))
                    && (Long) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_AVAILABLE_CAPTURE_COUNT, index)) > 0L
                    && (Integer) keyManager.getValue(CameraKey.create(CameraKey.INNERSTORAGE_AVAILABLE_RECORDING_TIME_IN_SECONDS, index)) > 0);
        }
        return false;
    }

    /**
     * Check Storage States
     */
    private void checkStorageStates() {
        KeyManager keyManager = KeyManager.getInstance();
        DJIKey storageLocationkey = CameraKey.create(CameraKey.CAMERA_STORAGE_LOCATION, MAIN_CAMERA_INDEX);
        Object storageLocationObj = keyManager.getValue(storageLocationkey);
        SettingsDefinitions.StorageLocation storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;

        if (storageLocationObj instanceof SettingsDefinitions.StorageLocation){
            storageLocation = (SettingsDefinitions.StorageLocation) storageLocationObj;
        }

        if (storageLocation == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) {
            if (!isInteralStorageReady(MAIN_CAMERA_INDEX) && isSDCardReady(MAIN_CAMERA_INDEX)) {
                switchStorageLocation(SettingsDefinitions.StorageLocation.SDCARD);
            }
        }

        if (storageLocation == SettingsDefinitions.StorageLocation.SDCARD) {
            if (!isSDCardReady(MAIN_CAMERA_INDEX) && isInteralStorageReady(MAIN_CAMERA_INDEX)) {
                switchStorageLocation(SettingsDefinitions.StorageLocation.INTERNAL_STORAGE);
            }
        }

        DJIKey isRecordingKey = CameraKey.create(CameraKey.IS_RECORDING, MAIN_CAMERA_INDEX);
        Object isRecording = keyManager.getValue(isRecordingKey);
        if (isRecording instanceof Boolean) {
            if (((Boolean) isRecording).booleanValue()) {
                keyManager.performAction(CameraKey.create(CameraKey.STOP_RECORD_VIDEO, MAIN_CAMERA_INDEX), new ActionCallback() {
                    @Override
                    public void onSuccess() {
                        setResultToToast("Stop Recording Success!");
                    }

                    @Override
                    public void onFailure(@NonNull DJIError error) {
                        setResultToToast("Stop Recording Fail，Error " + error.getDescription());
                    }
                });
            }
        }
    }

    /**
     * Clear MultiTracking View
     */
    private void clearCurrentView() {
        if (targetViewHashMap != null && !targetViewHashMap.isEmpty()) {
            Iterator<Map.Entry<Integer, MultiTrackingView>> it = targetViewHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, MultiTrackingView> entry = it.next();
                final MultiTrackingView view = entry.getValue();
                it.remove();
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBgLayout.removeView(view);
                    }
                });
            }
        }
    }
}