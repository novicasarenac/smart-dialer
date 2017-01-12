package com.smartdialer;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    Button callNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        callNumber = (Button)findViewById(R.id.callNumber);
    }

    public void takePhoto(View view){
        Intent activityPhotoIntent = new Intent(this, PhotoIntentActivity.class);
        startActivity(activityPhotoIntent);
    }
}
