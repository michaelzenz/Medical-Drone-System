package com.airlife.control;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SlidingDrawer;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.airlife.control.env.BorderedText;
import com.airlife.control.env.ImageUtils;
import com.airlife.control.env.Logger;
import com.airlife.control.tracking.MultiBoxTracker;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.mission.activetrack.ActiveTrackMission;
import dji.common.mission.activetrack.ActiveTrackMissionEvent;
import dji.common.mission.activetrack.ActiveTrackMode;
import dji.common.mission.activetrack.ActiveTrackState;
import dji.common.mission.activetrack.ActiveTrackTargetState;
import dji.common.mission.activetrack.ActiveTrackTrackingState;
import dji.common.mission.activetrack.SubjectSensingState;
import dji.common.util.CommonCallbacks.CompletionCallback;
import dji.common.util.CommonCallbacks.CompletionCallbackWith;
import dji.keysdk.CameraKey;
import dji.keysdk.DJIKey;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.keysdk.callback.ActionCallback;
import dji.keysdk.callback.SetCallback;
import dji.log.DJILog;
import dji.midware.media.DJIVideoDataRecver;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.activetrack.ActiveTrackMissionOperatorListener;
import dji.sdk.mission.activetrack.ActiveTrackOperator;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainControlActivity extends BaseActivity implements OnClickListener, ActiveTrackMissionOperatorListener {

    private AutoController controller=null;

    private static final String TAG = "TrackingTestActivity";
    private static final int MAIN_CAMERA_INDEX = 0;
    private static final int INVAVID_INDEX = -1;
    private static final int MOVE_OFFSET = 20;

    private RelativeLayout.LayoutParams layoutParams;
    private Switch mAutoSensingSw;
    private ImageButton mPushDrawerIb;
    private SlidingDrawer mPushInfoSd;
    private ImageButton mStopBtn;
    private ImageView mTrackingImage;
    private RelativeLayout mBgLayout;
    private TextView mPushInfoTv;
    private Switch mPushBackSw;
    private ImageView mSendRectIV;
    private Button mConfigBtn;
    private Button mConfirmBtn;
    private Button mRejectBtn;
    private Button mStartBtn;
    private Button mEndBtn;
    private Button mTestBtn;

    private ActiveTrackOperator mActiveTrackOperator;
    private ActiveTrackMission mActiveTrackMission;
    private final DJIKey trackModeKey = FlightControllerKey.createFlightAssistantKey(FlightControllerKey.ACTIVE_TRACK_MODE);
    private ConcurrentHashMap<Integer, MultiTrackingView> targetViewHashMap = new ConcurrentHashMap<>();
    private int trackingIndex = INVAVID_INDEX;
    private boolean isAutoSensingSupported = false;
    private ActiveTrackMode startMode = ActiveTrackMode.TRACE;

    private boolean isTrackingObject = false;


    /*******************************/
    //TensorFlow
    /*******************************/


    private static final Logger LOGGER = new Logger();
    // Configuration values for the prepackaged multibox model.
    private static final int MB_INPUT_SIZE = 224;
    private static final int MB_IMAGE_MEAN = 128;
    private static final float MB_IMAGE_STD = 128;
    private static final String MB_INPUT_NAME = "ResizeBilinear";
    private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
    private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
    private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
    private static final String MB_LOCATION_FILE =
            "file:///android_asset/multibox_location_priors.txt";

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

    // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
    // must be manually placed in the assets/ directory by the user.
    // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
    // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
    // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
    private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
    private static final String YOLOv2_MODEL_FILE = "file:///android_asset/yolov2-tiny.pb";
    private static final int YOLO_INPUT_SIZE = 416;
    private static final String YOLO_INPUT_NAME = "input";
    private static final String YOLO_OUTPUT_NAMES = "output";
    private static final int YOLO_BLOCK_SIZE = 32;

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
    // or YOLO.
    private enum DetectorMode {
        TF_OD_API, MULTIBOX, YOLO, YOLOv2;
    }
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
    private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;

    private static final boolean MAINTAIN_ASPECT = (MODE == DetectorMode.YOLO)||(MODE == DetectorMode.YOLOv2);

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private static final boolean SAVE_PREVIEW_BITMAP = true;
    private static final float TEXT_SIZE_DIP = 10;

    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private byte[] luminanceCopy;

    private BorderedText borderedText;
    private RectF objectFoundLoaction;
    private onObjectRecognizedHandler handler;
    /**********************/
    //!TensorFlow
    /**********************/


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_tracking_test);
        mContext=getApplicationContext();
        super.onCreate(savedInstanceState);
        handler=new onObjectRecognizedHandler(this);
        initUI();
    }

    /**
     * InitUI
     */
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
        mStartBtn=(Button)findViewById(R.id.btn_start);
        mEndBtn=(Button)findViewById(R.id.btn_end);
        mTestBtn=(Button)findViewById(R.id.btn_test);

        mAutoSensingSw.setChecked(false);
        mPushBackSw.setChecked(false);
        mConfirmBtn.setOnClickListener(this);
        mStopBtn.setOnClickListener(this);
        mRejectBtn.setOnClickListener(this);
        mConfigBtn.setOnClickListener(this);
        mPushDrawerIb.setOnClickListener(this);
        mStartBtn.setOnClickListener(this);
        mEndBtn.setOnClickListener(this);
        mTestBtn.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initMissionManager();
    }

    private static class onObjectRecognizedHandler extends Handler{
        public static final int MSG_OBJECT_FOUND=0;
        public static final int MSG_OBJECT_LOST=1;

        private WeakReference<MainControlActivity> mWeakActivity;

        public onObjectRecognizedHandler(MainControlActivity activity) {
            mWeakActivity = new WeakReference<MainControlActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg)
        {
            MainControlActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what){
                case MSG_OBJECT_FOUND:
                    activity.sendRect2Track();
                    break;
                case MSG_OBJECT_LOST:
                    activity.stopTracking();
                    break;
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
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
        mActiveTrackOperator.getRetreatEnabled(new CompletionCallbackWith<Boolean>() {
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


    /**
     * @Description : RETURN BTN RESPONSE FUNCTION
     */
    @Override
    public void onReturn(View view) {
        DJILog.d(TAG, "onReturn");
        DJISDKManager.getInstance().getMissionControl().destroy();
        this.finish();
    }

    @Override
    protected void onDestroy() {
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

    private double calcManhattanDistance(double point1X, double point1Y, double point2X,
                                         double point2Y) {
        return Math.abs(point1X - point2X) + Math.abs(point1Y - point2Y);
    }

    void sendRect2Track()
    {
        sendRect2Track(objectFoundLoaction);
    }

    void sendRect2Track(RectF rect2track)
    {
        sendRect2Track(rect2track.left,rect2track.top,rect2track.right,rect2track.bottom);
    }

    void sendRect2Track(float left, float top, float right, float bottom)
    {
        if(isTrackingObject)return;

        RectF rectF=new RectF(left,top,right,bottom);
        mActiveTrackMission = new ActiveTrackMission(rectF, startMode);
        mActiveTrackOperator.startTracking(mActiveTrackMission, new CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if(error==null)isTrackingObject=true;
                setResultToToast("Start Tracking: " + (error == null
                        ? "Success"
                        : error.getDescription()));
            }
        });

        trackingIndex = INVAVID_INDEX;
        mActiveTrackOperator.acceptConfirmation(new CompletionCallback() {

            @Override
            public void onResult(DJIError error) {
                setResultToToast(error == null ? "Start Tracking!" : error.getDescription());
            }
        });
    }

    void stopTracking()
    {
        trackingIndex = INVAVID_INDEX;
        mActiveTrackOperator.stopTracking(new CompletionCallback() {

            @Override
            public void onResult(DJIError error) {
                if(error==null)isTrackingObject=false;
                setResultToToast(error == null ? "Stop track Success!" : error.getDescription());
            }
        });

        mTrackingImage.setVisibility(View.INVISIBLE);
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
    public void onClick(View v) {
        if (mActiveTrackOperator == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.tracking_drawer_control_ib:
                if (mPushInfoSd.isOpened()) {
                    mPushInfoSd.animateClose();
                } else {
                    mPushInfoSd.animateOpen();
                }
                break;

            case R.id.btn_end:
                if (null != controller) {
                    controller.Landing();
                } else {
                    showToast("Currently No Controller");
                }
                break;

            case R.id.btn_start:
                if (null == controller) {
                    controller = new AutoController(this);
                }
                controller.initFlightController();
                controller.TakeOff();
                showToast("Take Off Success");
                break;

            case R.id.btn_test:
                if (null != controller) {
                    showToast("Testing!");
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
    }

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

        MainControlActivity.this.runOnUiThread(new Runnable() {

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

        MainControlActivity.this.runOnUiThread(new Runnable() {

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
                MultiTrackingView trackingView = new MultiTrackingView(MainControlActivity.this);
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
     * Clear MultiTracking View
     */
    private void clearCurrentView() {
        if (targetViewHashMap != null && !targetViewHashMap.isEmpty()) {
            Iterator<Map.Entry<Integer, MultiTrackingView>> it = targetViewHashMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, MultiTrackingView> entry = it.next();
                final MultiTrackingView view = entry.getValue();
                it.remove();
                MainControlActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBgLayout.removeView(view);
                    }
                });
            }
        }
    }

    /**************TensorFlow***************/

    OverlayView trackingOverlay;
    @Override
    public void onPreviewSizeChosen(final int Width,final int Height) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;
        if (MODE == DetectorMode.YOLO) {
            detector =
                    TensorFlowYoloDetector.create(
                            getAssets(),
                            YOLO_MODEL_FILE,
                            YOLO_INPUT_SIZE,
                            YOLO_INPUT_NAME,
                            YOLO_OUTPUT_NAMES,
                            YOLO_BLOCK_SIZE);
            cropSize = YOLO_INPUT_SIZE;
        }else if (MODE == DetectorMode.YOLOv2) {
            detector =
                    TensorFlowYolov2Detector.create(
                            getAssets(),
                            YOLOv2_MODEL_FILE,
                            YOLO_INPUT_SIZE,
                            YOLO_INPUT_NAME,
                            YOLO_OUTPUT_NAMES,
                            YOLO_BLOCK_SIZE);
            cropSize = YOLO_INPUT_SIZE;
        }
        else if (MODE == DetectorMode.MULTIBOX) {
            detector =
                    TensorFlowMultiBoxDetector.create(
                            getAssets(),
                            MB_MODEL_FILE,
                            MB_LOCATION_FILE,
                            MB_IMAGE_MEAN,
                            MB_IMAGE_STD,
                            MB_INPUT_NAME,
                            MB_OUTPUT_LOCATIONS_NAME,
                            MB_OUTPUT_SCORES_NAME);
            cropSize = MB_INPUT_SIZE;
        } else {
            try {
                detector = TensorFlowObjectDetectionAPIModel.create(
                        getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
                cropSize = TF_OD_API_INPUT_SIZE;
            } catch (final IOException e) {
                LOGGER.e("Exception initializing classifier!", e);
                Toast toast =
                        Toast.makeText(
                                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
                toast.show();
                finish();
            }
        }

        previewWidth = Width;
        previewHeight = Height;

        //sensorOrientation = rotation - getScreenOrientation();
        sensorOrientation = 0;

        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.mtracking_overlay);
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });
        addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        if (!isDebug()) {
                            return;
                        }
                        final Bitmap copy = cropCopyBitmap;
                        if (copy == null) {
                            return;
                        }

                        final int backgroundColor = Color.argb(100, 0, 0, 0);
                        canvas.drawColor(backgroundColor);

                        final Matrix matrix = new Matrix();
                        final float scaleFactor = 2;
                        matrix.postScale(scaleFactor, scaleFactor);
                        matrix.postTranslate(
                                canvas.getWidth() - copy.getWidth() * scaleFactor,
                                canvas.getHeight() - copy.getHeight() * scaleFactor);
                        canvas.drawBitmap(copy, matrix, new Paint());

                        final Vector<String> lines = new Vector<String>();
                        if (detector != null) {
                            final String statString = detector.getStatString();
                            final String[] statLines = statString.split("\n");
                            for (final String line : statLines) {
                                lines.add(line);
                            }
                        }
                        lines.add("");

                        lines.add("Frame: " + previewWidth + "x" + previewHeight);
                        lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
                        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
                        lines.add("Rotation: " + sensorOrientation);
                        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

                        borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
                    }
                });
    }

    @Override
    protected void processImage()
    {
        ++timestamp;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
        tracker.onFrame(
                previewWidth,
                previewHeight,
                getLuminanceStride(),
                sensorOrientation,
                originalLuminance,
                timestamp);
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                            case MULTIBOX:
                                minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
                                break;
                            case YOLO:
                                minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);

                                //for drone to track
                                objectFoundLoaction=location;
                                handler.sendEmptyMessage(onObjectRecognizedHandler.MSG_OBJECT_FOUND);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                        trackingOverlay.postInvalidate();

                        //requestRender();
                        computingDetection = false;
                    }
                });
    }

    @Override
    public void onSetDebug(final boolean debug) {
        detector.enableStatLogging(debug);
    }

    /**************************/
    //!TensorFlow
    /**************************/

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
     * Toast
     *
     * @param string
     */
    private void setResultToToast(final String string) {
        MainControlActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainControlActivity.this, string, Toast.LENGTH_SHORT).show();
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
        MainControlActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPushInfoTv.setText(string);
            }
        });
    }

}
