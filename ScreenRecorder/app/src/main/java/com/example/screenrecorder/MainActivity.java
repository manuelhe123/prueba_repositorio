package com.example.screenrecorder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.R.id;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private ToggleButton toggleButton;
    private Chronometer chronoMeter;
    private TextView readText;

    private int screenDensity;
    private MediaRecorder mediaRecorder;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjectionCallback mediaProjectionCallback;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    private static final String TAG="MainActivity";
    private static final int DISPLAY_WIDTH=720;
    private static final int DISPLAY_HEIGHT=1280;
    private static final int REQUEST_PERMISSION=10;
    private static final int REQUEST_CODE=1000;
    private String videoUrl="";
    private static final SparseIntArray ORIENTATION= new SparseIntArray();
    static {
        ORIENTATION.append(Surface.ROTATION_0,90);
        ORIENTATION.append(Surface.ROTATION_0,0);
        ORIENTATION.append(Surface.ROTATION_0,270);
        ORIENTATION.append(Surface.ROTATION_0,180);

    }





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chronoMeter = (Chronometer) findViewById(R.id.cmTimer);
        readText=(TextView)findViewById(R.id.tvRecord);
        toggleButton=(ToggleButton)findViewById(R.id.tbSwitch);

        DisplayMetrics metrics=new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        screenDensity=metrics.densityDpi;
        mediaRecorder=new MediaRecorder();
        mediaProjectionManager=(MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)+
                ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.RECORD_AUDIO)!=
                        PackageManager.PERMISSION_GRANTED)
                {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.RECORD_AUDIO))
                    {
                        toggleButton.setChecked(false);
                        Snackbar.make(findViewById(android.R.id.content),
                        R.string.permission_text,Snackbar.LENGTH_INDEFINITE).setAction("ENABLE", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        Manifest.permission.RECORD_AUDIO},REQUEST_PERMISSION);
                            }
                        }).show();

                    }
                    else {
                        ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO},REQUEST_PERMISSION);
                     }
                }
                else {
                    onScreenShare(v);
                }

            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode!=REQUEST_CODE)
        {
            return;
        }
        if (resultCode!=RESULT_OK)
        {
            Toast.makeText(this,"Permiso denegadoxd",Toast.LENGTH_SHORT).show();
            toggleButton.setChecked(false);
            return;
        }

            mediaProjectionCallback= new MediaProjectionCallback();
            mediaProjection=mediaProjectionManager.getMediaProjection(resultCode,data);
            mediaProjection.registerCallback(mediaProjectionCallback,null);

            virtualDisplay=createVirtualDisplay();
            mediaRecorder.start();

    }

    private VirtualDisplay createVirtualDisplay() {
        return mediaProjection.createVirtualDisplay(TAG,DISPLAY_WIDTH,DISPLAY_HEIGHT,screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,mediaRecorder.getSurface(),null,null);
    }

    private void onScreenShare(View v) {

        if (((ToggleButton) v).isChecked())
        {
            readText.setVisibility(View.VISIBLE);
            chronoMeter.start();

            initiateRecorder();
            shareScreen();
        }
        else {
            readText.setVisibility(View.INVISIBLE);
            mediaRecorder.stop();
            mediaRecorder.reset();

            stopScreenSharing();
            chronoMeter.stop();
            chronoMeter.setBase(SystemClock.elapsedRealtime());

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode)
        {
            case REQUEST_PERMISSION: {
                if ((grantResults.length >0) &&(grantResults[0] +
                        grantResults[1])== PackageManager.PERMISSION_GRANTED)
                {
                    onScreenShare(toggleButton);
                }
                else{
                    toggleButton.setChecked(false);
                    Snackbar.make(findViewById(android.R.id.content),R.string.permission_text,
                            Snackbar.LENGTH_INDEFINITE).setAction("ENABLE", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent=new Intent();
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

                            startActivity(intent);
                        }
                    }).show();
                }
                return;
            }
        }
    }

    private void stopScreenSharing() {
        if (virtualDisplay==null)
        {
            return;
        }
        virtualDisplay.release();
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if (mediaProjection !=null)
        {
            mediaProjection.unregisterCallback(mediaProjectionCallback);
            mediaProjection.stop();
            mediaProjection=null;
        }
    }

    private void shareScreen() {
        if (mediaProjection==null)
        {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),REQUEST_CODE);
            return;
        }
        virtualDisplay=createVirtualDisplay();
        mediaRecorder.start();
    }

    private void initiateRecorder() {
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

            videoUrl= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+
            new StringBuilder("/KiranScreenRecorder ").append(new SimpleDateFormat("dd-MM-yyyy-hh_mm_ss").format(new Date())).append(".mp4").toString();
            mediaRecorder.setOutputFile(videoUrl);
            mediaRecorder.setVideoSize(DISPLAY_WIDTH,DISPLAY_HEIGHT);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncodingBitRate(512*1000);

            int rotation=getWindowManager().getDefaultDisplay().getRotation();
            int orientation=ORIENTATION.get(rotation + 90);

            mediaRecorder.setOrientationHint(orientation);
            mediaRecorder.prepare();

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {

        public void onStop()
        {
            if (toggleButton.isChecked())
            {
                toggleButton.setChecked(false);
                mediaRecorder.stop();
                mediaRecorder.reset();
            }
            mediaProjection=null;
            stopScreenSharing();
        }

    }
}