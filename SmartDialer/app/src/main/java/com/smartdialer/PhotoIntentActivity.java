package com.smartdialer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.smartdialer.directoryFactories.AlbumStorageDirectoryFactory;
import com.smartdialer.directoryFactories.BaseAlbumDirectoryFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PhotoIntentActivity extends AppCompatActivity {

    private String currentPhotoPath;
    static final int REQUEST_TAKE_PHOTO = 1;
    AlbumStorageDirectoryFactory albumStorageDirectoryFactory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_intent);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
            albumStorageDirectoryFactory = new BaseAlbumDirectoryFactory();

        dispatchTakePictureIntent();
    }

    //photo album for images
    private String getAlbumName(){
        return getString(R.string.album_name);
    }

    //directory of album
    private File getAlbumDirectory(){
        File storageDir = null;
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
            storageDir = albumStorageDirectoryFactory.getAlbumStorageDirectory(getAlbumName());
            if(storageDir != null){
                if(!storageDir.mkdirs()){
                    if(!storageDir.exists()){
                        Log.d("Photos", "failed to create directory");
                        return null;
                    }
                }
            }
        }else{
            Log.v(getString(R.string.app_name), "storage is not READABLE/WRITABLE");
        }

        return storageDir;
    }

    private File createImageFile() throws IOException{
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getAlbumDirectory();
        File image = File.createTempFile(imageFileName, "jpg", storageDir);

        //save file path
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if(takePictureIntent.resolveActivity(getPackageManager()) != null){
            //file where photo will go
            File photoFile = null;
            try{
                photoFile = createImageFile();
            }catch (IOException e){
                e.printStackTrace();
            }

            if(photoFile != null){
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }
}