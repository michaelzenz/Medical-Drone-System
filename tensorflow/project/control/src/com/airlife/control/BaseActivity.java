package com.airlife.control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.airlife.control.env.ImageUtils;
import com.airlife.control.env.Logger;
import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.OffscreenSurface;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;

import java.util.ArrayList;

import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public abstract class BaseActivity extends FragmentActivity{

    private static final String TAG = MainActivity.class.getName();
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;
    protected DJICodecManager mCodecManager = null;
    private static BaseProduct mProduct=null;

    //To store index chosen in PopupNumberPicker listener
    protected static int[] INDEX_CHOSEN = {-1, -1, -1};

    protected TextView mConnectStatusTextView;

    //private SurfaceTexture mSurfaceTexture=null;
    protected AutoFitSurfaceView mSurfaceView;

    //TensorFlow
    private static final Logger LOGGER = new Logger();

    private Handler handler;
    private HandlerThread handlerThread;

    private boolean debug = false;

    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private byte[] LastPreviewBytes;
    private int yRowStride;
    private final Size DesiredSize=new Size(640,480);

    protected int previewWidth = DesiredSize.getWidth();
    protected int previewHeight = DesiredSize.getHeight();

    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Runnable luminanceConverter;

    //!TensorFlow
    
    //Render SurfaceTexture
    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private OffscreenSurface mOffscreenSurface;
    private FullFrameRect mFullFrameBlit;
    private int mTextureId=0;
    private float[] TransformMatrixValues=new float[16];
    private SurfaceTexture mSurfaceTexture;

    protected Context mContext=null;
    //!Render SurfaceTexture

    SurfaceHolder.Callback mSurfaceViewCallback=new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {

            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
            mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
            mDisplaySurface.makeCurrent();

            mFullFrameBlit = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
            mTextureId = mFullFrameBlit.createTextureObject();
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mSurfaceTexture.setOnFrameAvailableListener(mFrameAvailableListener);
            mSurfaceTexture.setDefaultBufferSize(mSurfaceView.getWidth(),mSurfaceView.getHeight());
            mOffscreenSurface=new OffscreenSurface(mEglCore,previewWidth,previewHeight);

            if (mCodecManager == null&&mContext!=null) {
                //which context to use?
                mCodecManager = new DJICodecManager(getApplicationContext(), mSurfaceTexture, mSurfaceView.getWidth(), mSurfaceView.getHeight());
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (mCodecManager != null) {
                mCodecManager.cleanSurface();
                mCodecManager.destroyCodec();
                mCodecManager = null;
            }
        }
    };

    SurfaceTexture.OnFrameAvailableListener mFrameAvailableListener=new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(TransformMatrixValues);

            // Fill the SurfaceView with it.
            mDisplaySurface.makeCurrent();
            GLES20.glViewport(0, 0, mSurfaceView.getWidth(), mSurfaceView.getHeight());
            mFullFrameBlit.drawFrame(mTextureId, TransformMatrixValues);
            mDisplaySurface.swapBuffers();
//
            if (isProcessingFrame) {
                LOGGER.w("Dropping frame!");
                return;
            }
            isProcessingFrame = true;

            mOffscreenSurface.makeCurrent();
            GLES20.glViewport(0, 0, previewWidth, previewHeight);
            mFullFrameBlit.drawFlippedFrame(mTextureId, TransformMatrixValues);
            LastPreviewBytes=mOffscreenSurface.getPixels();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertRGBAToARGB8888(LastPreviewBytes, previewWidth, previewHeight, rgbBytes);
                        }
                    };

            luminanceConverter= new Runnable() {
                @Override
                public void run() {
                    if(yuvBytes[0]==null)yuvBytes[0]=new byte[previewWidth*previewHeight];
                    ImageUtils.convertRGBAToLUMINANCE(LastPreviewBytes,previewWidth,previewHeight,yuvBytes[0]);
                }
            };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(savedInstanceState);
         
        IntentFilter filter = new IntentFilter();  
        filter.addAction(DJIBase.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);


        mSurfaceView=(AutoFitSurfaceView)findViewById(R.id.drone_surface);

        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);

        if (null != mSurfaceView) {
            mSurfaceView.setAspectRatio(DesiredSize.getWidth(),DesiredSize.getHeight());
            mSurfaceView.getHolder().addCallback(mSurfaceViewCallback);
            if(LastPreviewBytes==null)LastPreviewBytes=new byte[previewWidth*previewHeight*4];

            if(rgbBytes==null){
                yRowStride=previewWidth;
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(previewWidth,previewHeight);
            }

        } else {
            Log.e(TAG, "mSurfaceView is null");
        }

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if(mCodecManager != null){

                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
            onProductChange();
        }
        
    };

    public static synchronized BaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getProduct();
        }
        return mProduct;
    }
    
    protected void onProductChange() {
        initPreviewer();
    }

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof Aircraft;
    }

    public static synchronized Aircraft getAircraftInstance() {
        if (!isAircraftConnected()) return null;
        return (Aircraft) getProductInstance();
    }
    
    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();

        updateTitleBar();
        initPreviewer();

        //TensorFlow
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }
    
    @Override
    protected void onPause() {
        LOGGER.d("onPause " + this);

        if (!isFinishing()) {
            LOGGER.d("Requesting finish");
            finish();
        }

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }

        uninitPreviewer();
        super.onPause();
    }

    @Override
    protected void onStop() {
        LOGGER.d("onStop " + this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LOGGER.d("onDestroy " + this);
        unregisterReceiver(mReceiver);
        uninitPreviewer();
        super.onDestroy();
    }
    
    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(BaseActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void initPreviewer() {
        try {
            mProduct = DJIBase.getProductInstance();
        } catch (Exception exception) {
            mProduct = null;
        }
        
        if (mProduct == null || !mProduct.isConnected()) {
            Log.d(TAG, "Disconnect");
        } else {
            //if(mSurfaceTexture!=null)mSurfaceTexture.setOnFrameAvailableListener(mFrameAvailableListener);
            if (!mProduct.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
            }

        }
    }

    private void uninitPreviewer() {
        Camera camera = DJIBase.getCameraInstance();
        if (camera != null){
            // Reset the callback
            VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
        }
    }
    
    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        BaseProduct product = DJIBase.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                mConnectStatusTextView.setText(DJIBase.getProductInstance().getModel().getDisplayName() + " Connected");
                ret = true;
            } else {
                if(product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft)product;
                    if(aircraft.getRemoteController() != null) {
                    }
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }
        
        if(!ret) {
            //mConnectStatusTextView.setText("Disconnected");
        }
    }


    /************************/
    //TensorFlow
    /************************/

    public void onSetDebug(final boolean debug) {}

    public boolean isDebug() {
        return debug;
    }

    public void requestRender() {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.postInvalidate();
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            debug = !debug;
            requestRender();
            onSetDebug(debug);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getLuminanceStride() {
        return yRowStride;
    }

    protected byte[] getLuminance() {
        luminanceConverter.run();
        return yuvBytes[0];
    }

    public void addCallback(final OverlayView.DrawCallback callback) {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.addCallback(callback);
        }
    }

    protected abstract void processImage();
    protected abstract void onPreviewSizeChosen(final int previewWidth,final int previewHeight);

    /************************/
    //!TensorFlow
    /************************/



    /**
     * @Description : RETURN BTN RESPONSE FUNCTION
     * @author : andy.zhao
     * @param view
     * @return : void
     */
    public void onReturn(View view) {
        this.finish();
    }
    
    public void resetIndex() {
        INDEX_CHOSEN = new int[3];
        INDEX_CHOSEN[0] = -1;
        INDEX_CHOSEN[1] = -1;
        INDEX_CHOSEN[2] = -1;
    }
    
    public ArrayList<String> makeListHelper(Object[] o) {
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < o.length - 1; i++) {
            list.add(o[i].toString());
        }
        return list;
    }
}
