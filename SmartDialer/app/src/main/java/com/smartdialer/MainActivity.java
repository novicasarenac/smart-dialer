package com.smartdialer;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    Button callNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //OpenCVLoader openCVLoader = new OpenCVLoader();
        callNumber = (Button)findViewById(R.id.callNumber);
    }

    public void takePhoto(View view){
        Intent activityPhotoIntent = new Intent(this, PhotoIntentActivity.class);
        startActivity(activityPhotoIntent);
    }

    public void exit(View view){
        finish();
        this.finishAffinity();
    }

    public void writeNumber(View view){
        Intent writeNumberIntent = new Intent(this, WriteNumberActivity.class);
        startActivity(writeNumberIntent);
    }
}
