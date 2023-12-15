package com.example.imagepro;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 0;
    private static final String TAG = "CameraActivity";
    private static final String IMAGE_FOLDER_PATH = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES) + "/ImagePro/";

    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private facialExpressionRecognition facialExpressionRecognition;
    private Button captureButton;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCv Is loaded");
                    mOpenCvCameraView.enableView();
                }
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public CameraActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Check for camera permission
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        setContentView(R.layout.activity_camera);

        // Ensure the folder for saving images exists
        File folder = new File(IMAGE_FOLDER_PATH);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        captureButton = findViewById(R.id.capture_button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureImage();
            }
        });

        mOpenCvCameraView = findViewById(R.id.frame_Surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        try {
            int inputSize = 48;
            facialExpressionRecognition = new facialExpressionRecognition(getAssets(), CameraActivity.this,
                    "model300.tflite", inputSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void captureImage() {
        try {
            // Ensure the folder for saving images exists
            File folder = new File(IMAGE_FOLDER_PATH);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            // Create a filename with a timestamp to avoid overwriting
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = IMAGE_FOLDER_PATH + "captured_image_" + timeStamp + ".jpg";

            // Capture the current frame from the camera view
            Mat rgbaImage = mRgba.clone();  // Make a copy to avoid modifying the displayed frame

            // Crop the image to include only the face (adjust the coordinates accordingly)
            Rect faceRect = facialExpressionRecognition.getFaceCoordinates(rgbaImage); // Replace x, y, width, height with your face region coordinates
            if (faceRect != null) {
                // Crop the image to include only the face
                Mat croppedImage = new Mat(rgbaImage, faceRect);

                // Save the cropped image to the specified file
                Imgcodecs.imwrite(fileName, croppedImage);

                // Release the Mats to avoid memory leaks
                rgbaImage.release();
                croppedImage.release();

                // Notify the media scanner to recognize the new image
                MediaScannerConnection.scanFile(this, new String[]{fileName}, null, new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(TAG, "Image scanned: " + path);
                    }
                });
            }
            // Implement further processing with the captured image data
            // For example, you can save it to a file or send it to a server

            Toast.makeText(CameraActivity.this, "Image Captured and Saved!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            // Log the exception or display a message to help identify the issue
            Toast.makeText(CameraActivity.this, "Error capturing image", Toast.LENGTH_SHORT).show();
            Log.d(TAG,"Error: " +  e.getMessage());
        }
    }




    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "Opencv initialization is done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            Log.d(TAG, "Opencv is not loaded. try again");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        mRgba = facialExpressionRecognition.recognizeImage(mRgba);
        return mRgba;
    }
}
