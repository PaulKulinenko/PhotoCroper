package com.testcrop.myapplication;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "saver";
    private int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    TextureView textureView;
    FrameLayout border;
    ImageCapture imgCap;
    ImageView imageView;
    ImageView btnCancel;
    ImageView btnMakePhoto;
    int сameraWidth;
    int cameraHeight;
    int xSrcreenWidth;
    int ySrcreenHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.view_finder);
        border = findViewById(R.id.crop_area);
        imageView = findViewById(R.id.iv_image);
        btnCancel = findViewById(R.id.btn_cancel);
        btnMakePhoto = findViewById(R.id.imgCapture);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnMakePhoto.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.GONE);
                imageView.setVisibility(View.GONE);
            }
        });

        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }


    private void startCamera() {
        CameraX.unbindAll();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        xSrcreenWidth = size.x;
        ySrcreenHeight = size.y;

        Rational aspectRatio = new Rational(xSrcreenWidth, ySrcreenHeight);
        Size screen = new Size(xSrcreenWidth, ySrcreenHeight); //size of the screen


        PreviewConfig pConfig = new PreviewConfig.Builder().setTargetAspectRatio(aspectRatio).setTargetResolution(screen).build();
        Preview preview = new Preview(pConfig);

        preview.setOnPreviewOutputUpdateListener(
                new Preview.OnPreviewOutputUpdateListener() {
                    //to update the surface texture we  have to destroy it first then re-add it
                    @Override
                    public void onUpdated(Preview.PreviewOutput output) {
                        ViewGroup parent = (ViewGroup) textureView.getParent();
                        parent.removeView(textureView);
                        parent.addView(textureView, 0);
                        сameraWidth = output.getTextureSize().getWidth();
                        cameraHeight = output.getTextureSize().getHeight();
                        textureView.setSurfaceTexture(output.getSurfaceTexture());
                        updateTransform();
                    }
                });


        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();
        imgCap = new ImageCapture(imageCaptureConfig);


        btnMakePhoto.setVisibility(View.VISIBLE);
        btnMakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + ".png");
                imgCap.takePicture(file, new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(@NonNull File file) {

                        Bitmap realImage;

                        try {
                            realImage = BitmapFactory.decodeFile(file.getAbsolutePath());
                            ExifInterface exif = new ExifInterface(file.toString());
                            if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("6")) {
                                realImage = rotate(realImage, 90);
                            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("8")) {
                                realImage = rotate(realImage, 270);
                            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("3")) {
                                realImage = rotate(realImage, 180);
                            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("0")) {
                                realImage = rotate(realImage, 45);
                            }

                            int xPrevWidth = textureView.getMeasuredWidth();
                            int yPrevHeight = textureView.getMeasuredHeight();

                            int xRealWidth = realImage.getWidth();
                            int yRealHeight = realImage.getHeight();

                            float koef = (float) yRealHeight / сameraWidth;
                            if (xRealWidth / xPrevWidth >= 1 && yRealHeight / yPrevHeight >= 1) {
                                float cropXvalue = (xRealWidth - (koef * cameraHeight)) / 2;
                                System.out.println("cropXvalue = " + cropXvalue);
                                Bitmap cropedBItmap = applyCrop(realImage, (int) cropXvalue, 0, (int) cropXvalue, 0);

                                Bitmap scaled = Bitmap.createScaledBitmap(cropedBItmap, cameraHeight, сameraWidth, false);

                                //get border  size and position on the screen
                                int x1 = border.getLeft();
                                int y1 = border.getTop();
                                int x2 = border.getWidth();
                                int y2 = border.getHeight();

                                int cropStartX = Math.round((float) x1 / xSrcreenWidth * cameraHeight);
                                int cropStartY = Math.round((float) y1 / ySrcreenHeight * сameraWidth);

                                int cropWidthX = Math.round((float) x2 / xSrcreenWidth * cameraHeight);
                                int cropHeightY = Math.round((float) y2 / ySrcreenHeight * сameraWidth);

                                Bitmap rezult = Bitmap.createBitmap(scaled, cropStartX,
                                        cropStartY, cropWidthX, cropHeightY);
                               // createImageFile(rezult);

                                imageView.setImageBitmap(rezult);
                                imageView.setVisibility(View.VISIBLE);
                                btnCancel.setVisibility(View.VISIBLE);
                                btnMakePhoto.setVisibility(View.GONE);
                            }

                        } catch (FileNotFoundException e) {
                            Log.d("Info", "File not found: " + e.getMessage());
                        } catch (IOException e) {
                            Log.d("TAG", "Error accessing file: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCapture.UseCaseError useCaseError, @NonNull String message, @Nullable Throwable cause) {
                        String msg = "Pic capture failed : " + message;
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
                        if (cause != null) {
                            cause.printStackTrace();
                        }
                    }
                });
            }
        });

        //bind to lifecycle:
        CameraX.bindToLifecycle(this, preview, imgCap);
    }


    public Bitmap applyCrop(Bitmap bitmap, int leftCrop, int topCrop, int rightCrop, int bottomCrop) {
        int cropWidth = bitmap.getWidth() - rightCrop - leftCrop;
        int cropHeight = bitmap.getHeight() - bottomCrop - topCrop;
        return Bitmap.createBitmap(bitmap, leftCrop, topCrop, cropWidth, cropHeight);
    }

    public static Bitmap rotate(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix mtx = new Matrix();
        mtx.setRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    private void createImageFile(Bitmap bitmap) {
        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);

        String timeStamp = new SimpleDateFormat("MMdd_HHmmssSSS").format(new Date());
        String imageFileName = "region_" + timeStamp + ".jpg";
        final File file = new File(path, imageFileName);

        try {
            // Make sure the Pictures directory exists.
            if (path.mkdirs()) {
                Toast.makeText(this, "Not exist :" + path.getName(), Toast.LENGTH_SHORT).show();
            }

            OutputStream os = new FileOutputStream(file);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);

            os.flush();
            os.close();
            Log.i("ExternalStorage", "Writed " + path + file.getName());
            // Tell the media scanner about the new file so that it is
            // immediately available to the user.
            MediaScannerConnection.scanFile(this,
                    new String[]{file.toString()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }
                    });
            Toast.makeText(this, file.getName(), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            // Unable to create file, likely because external storage is
            // not currently mounted.
            Log.w("ExternalStorage", "Error writing " + file, e);
        }
    }


    private void updateTransform() {
        Matrix mx = new Matrix();
        float w = textureView.getMeasuredWidth();
        float h = textureView.getMeasuredHeight();

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationDgr;
        int rotation = (int) textureView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, cX, cY);
        textureView.setTransform(mx);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}