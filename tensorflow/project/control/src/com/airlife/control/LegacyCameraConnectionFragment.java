package com.airlife.control;

/*
 * Copyright 2017 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Environment;
import android.app.Fragment;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.airlife.control.env.ImageUtils;
import com.airlife.control.env.Logger;
import com.airlife.control.R; // Explicit import needed for internal Google builds.
import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.OffscreenSurface;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;

import static android.opengl.GLES10.GL_PROJECTION;
import static android.opengl.GLES10.glLoadIdentity;
import static android.opengl.GLES10.glMatrixMode;

public class LegacyCameraConnectionFragment extends Fragment {
  private Camera camera;
  private static final Logger LOGGER = new Logger();
  private Camera.PreviewCallback imageListener;
  private Size desiredSize;

  private EglCore mEglCore;
  private WindowSurface mDisplaySurface;
  private OffscreenSurface mOffscreenSurface;
  private SurfaceTexture mSurfaceTexture;  // receives the output from the camera preview
  private FullFrameRect mFullFrameBlit;
  private final float[] mTmpMatrix = new float[16];
  private int mTextureId;

  private static final int MINIMUM_PREVIEW_SIZE = 320;

  /**
   * An {@link AutoFitTextureView} for camera preview.
   */
  private AutoFitSurfaceView mSurfaceView;

  /**
   * An additional thread for running tasks that shouldn't block the UI.
   */
  private HandlerThread backgroundThread;

  /**
   * The layout identifier to inflate for this Fragment.
   */
  private int layout;

  public LegacyCameraConnectionFragment(
      final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize) {
    this.imageListener = imageListener;
    this.layout = layout;
    this.desiredSize = desiredSize;
  }

  /**
   * Conversion from screen rotation to JPEG orientation.
   */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  SurfaceTexture.OnFrameAvailableListener mFrameAvailableListener=new SurfaceTexture.OnFrameAvailableListener() {
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
      mSurfaceTexture.updateTexImage();
      mSurfaceTexture.getTransformMatrix(mTmpMatrix);

      // Fill the SurfaceView with it.
      mDisplaySurface.makeCurrent();
      GLES20.glViewport(0, 0, mSurfaceView.getWidth(), mSurfaceView.getHeight());
      mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);
      mDisplaySurface.swapBuffers();

      mOffscreenSurface.makeCurrent();
      GLES20.glViewport(0, 0, desiredSize.getWidth(), desiredSize.getHeight());
      mFullFrameBlit.drawFlippedFrame(mTextureId, mTmpMatrix);
      mOffscreenSurface.getPixels();
    }
  };


  SurfaceHolder.Callback mSurfaceHolderCallback=new SurfaceHolder.Callback() {
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

      mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
//      mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
//      mDisplaySurface.makeCurrent();
      mOffscreenSurface=new OffscreenSurface(mEglCore,desiredSize.getWidth(), desiredSize.getHeight());
      mOffscreenSurface.makeCurrent();
      mFullFrameBlit = new FullFrameRect(
              new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
      mTextureId = mFullFrameBlit.createTextureObject();
      mSurfaceTexture = new SurfaceTexture(mTextureId);
      mSurfaceTexture.setOnFrameAvailableListener(mFrameAvailableListener);

      openCamera();

      camera.startPreview();


    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
  };

  private void openCamera(){
    int index = getCameraId();
    camera = Camera.open(index);

    try {
      Camera.Parameters parameters = camera.getParameters();
      List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes != null
              && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      }
      List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
      Size[] sizes = new Size[cameraSizes.size()];
      int i = 0;
      for (Camera.Size size : cameraSizes) {
        sizes[i++] = new Size(size.width, size.height);
      }
      Size previewSize =
              chooseOptimalSize(
                      sizes, desiredSize.getWidth(), desiredSize.getHeight());
      parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
      camera.setDisplayOrientation(90);
      camera.setParameters(parameters);
      camera.setPreviewTexture(mSurfaceTexture);
    } catch (IOException exception) {
      camera.release();
    }

    camera.setPreviewCallbackWithBuffer(imageListener);
    Camera.Size s = camera.getParameters().getPreviewSize();
    camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);
    mSurfaceView.setAspectRatio(s.height, s.width);
  }

  @Override
  public View onCreateView(
      final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(layout, container, false);
  }

  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    mSurfaceView=(AutoFitSurfaceView) view.findViewById(R.id.camera_surface_view);
    SurfaceHolder sh = mSurfaceView.getHolder();
    sh.addCallback(mSurfaceHolderCallback);
  }

  @Override
  public void onActivityCreated(final Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();
    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).

    if (mSurfaceTexture!=null) {
      camera.startPreview();
      mSurfaceTexture.setOnFrameAvailableListener(mFrameAvailableListener);
    }
  }

  @Override
  public void onPause() {
    stopCamera();
    stopBackgroundThread();
    super.onPause();
  }

  /**
   * Starts a background thread and its {@link Handler}.
   */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("CameraBackground");
    backgroundThread.start();
  }

  /**
   * Stops the background thread and its {@link Handler}.
   */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  protected void stopCamera() {
    if (camera != null) {
      camera.stopPreview();
      camera.setPreviewCallback(null);
      camera.release();
      camera = null;
    }
  }

  private int getCameraId() {
    CameraInfo ci = new CameraInfo();
    for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
      Camera.getCameraInfo(i, ci);
      if (ci.facing == CameraInfo.CAMERA_FACING_BACK)
        return i;
    }
    return -1; // No camera found
  }

  /**
   * Compares two {@code Size}s based on their areas.
   */
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
    final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
    final Size desiredSize = new Size(width, height);

    // Collect the supported resolutions that are at least as big as the preview Surface
    boolean exactSizeFound = false;
    final List<Size> bigEnough = new ArrayList<Size>();
    final List<Size> tooSmall = new ArrayList<Size>();
    for (final Size option : choices) {
      if (option.equals(desiredSize)) {
        // Set the size but don't return yet so that remaining sizes will still be logged.
        exactSizeFound = true;
      }

      if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
        bigEnough.add(option);
      } else {
        tooSmall.add(option);
      }
    }

    LOGGER.i("Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
    LOGGER.i("Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
    LOGGER.i("Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

    if (exactSizeFound) {
      LOGGER.i("Exact size match found.");
      return desiredSize;
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
      LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
      return chosenSize;
    } else {
      LOGGER.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }
}
