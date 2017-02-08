package com.smartdialer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.smartdialer.directoryFactories.AlbumStorageDirectoryFactory;
import com.smartdialer.directoryFactories.BaseAlbumDirectoryFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WriteNumberActivity extends AppCompatActivity{

    LinearLayout content;
    PhoneNumberView phoneNumberView;
    public String currentPhotoPath = null;
    private Bitmap bitmap;
    File drawingFile;
    AlbumStorageDirectoryFactory albumStorageDirectoryFactory = null;
    public String imgName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (shouldAskPermissions()) {
            askPermissions();
        }

        setContentView(R.layout.activity_write_number);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
            albumStorageDirectoryFactory = new BaseAlbumDirectoryFactory();

        content = (LinearLayout)findViewById(R.id.linearLayout);
        phoneNumberView = new PhoneNumberView(this, null);
        phoneNumberView.setBackgroundColor(Color.WHITE);
        content.addView(phoneNumberView, android.app.ActionBar.LayoutParams.FILL_PARENT, ActionBar.LayoutParams.FILL_PARENT);

        //creating file for image
        try {
            drawingFile = createImageFile();
        }catch(Exception e){
            e.printStackTrace();
        }
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

                //asking permissions for creating directory
                if(shouldAskPermissions()) {
                    askPermissions();
                }

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

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getAlbumDirectory();
        File image = File.createTempFile(imageFileName, ".jpeg", storageDir);
        imgName = imageFileName;

        //save file path
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void saveDrawing(View view){
        view.setDrawingCacheEnabled(true);
        phoneNumberView.save(content);
        Intent callActivityIntent = new Intent(this, CallActivity.class);
        callActivityIntent.putExtra("imagePath", currentPhotoPath);
        startActivity(callActivityIntent);
    }

    public void clearDrawing(View view){
        phoneNumberView.clear();
    }

    public void cancel(View view){
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        startActivity(mainActivityIntent);
    }

    //region Panel for writing
    //Class for writing phone number on panel
    public class PhoneNumberView extends View {

        private static final float STROKE_WIDTH = 5f;
        private static final float HALF_STROKE_WIDTH = STROKE_WIDTH / 2;
        private Paint paint = new Paint();
        private Path path = new Path();

        private float lastTouchX;
        private float lastTouchY;
        private final RectF dirtyRect = new RectF();

        public PhoneNumberView(Context context, AttributeSet attrs) {
            super(context, attrs);

            //asking permissions for creating directory
            if(shouldAskPermissions()) {
                askPermissions();
            }

            paint.setAntiAlias(true);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(STROKE_WIDTH);
        }

        //check version
        protected boolean shouldAskPermissions() {
            return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
        }

        @TargetApi(23)
        protected void askPermissions() {
            String[] permissions = {
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.CALL_PHONE"
            };
            int requestCode = 200;
            requestPermissions(permissions, requestCode);
        }

        public void save(View view){

            //asking permissions for creating directory
            if(shouldAskPermissions()) {
                askPermissions();
            }

            if(bitmap == null){
                bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.RGB_565);
            }
            Canvas canvas = new Canvas(bitmap);
            try{
                //asking permissions for creating directory
                if(shouldAskPermissions()) {
                    askPermissions();
                }

                FileOutputStream fileOutputStream = new FileOutputStream(currentPhotoPath);

                view.draw(canvas);

                String url = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, imgName, null);

                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream);
                fileOutputStream.flush();
                fileOutputStream.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        public void clear(){
            path.reset();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float eventX = event.getX();
            float eventY = event.getY();

            switch (event.getAction()){
                case MotionEvent.ACTION_DOWN: {
                    path.moveTo(eventX, eventY);
                    lastTouchX = eventX;
                    lastTouchY = eventY;
                    return true;
                }

                case MotionEvent.ACTION_MOVE:

                case MotionEvent.ACTION_UP:{
                    resetDirtyRect(eventX, eventY);
                    int historySize = event.getHistorySize();
                    for(int i = 0; i < historySize; i++){
                        float historicalX = event.getHistoricalX(i);
                        float historicalY = event.getHistoricalY(i);
                        expandDirtyRect(historicalX, historicalY);
                        path.lineTo(historicalX, historicalY);
                    }
                    path.lineTo(eventX, eventY);
                    break;
                }

                default: return false;
            }

            invalidate((int) (dirtyRect.left - HALF_STROKE_WIDTH),
                       (int) (dirtyRect.top - HALF_STROKE_WIDTH),
                       (int) (dirtyRect.right + HALF_STROKE_WIDTH),
                       (int) (dirtyRect.bottom + HALF_STROKE_WIDTH));

            lastTouchX = eventX;
            lastTouchY = eventY;

            return true;
        }

        private void expandDirtyRect(float historicalX, float historicalY) {
            if (historicalX < dirtyRect.left) {
                dirtyRect.left = historicalX;
            }
            else if (historicalX > dirtyRect.right) {
                dirtyRect.right = historicalX;
            }

            if (historicalY < dirtyRect.top) {
                dirtyRect.top = historicalY;
            }
            else if (historicalY > dirtyRect.bottom) {
                dirtyRect.bottom = historicalY;
            }
        }

        private void resetDirtyRect(float eventX, float eventY) {
            dirtyRect.left = Math.min(lastTouchX, eventX);
            dirtyRect.right = Math.max(lastTouchX, eventX);
            dirtyRect.top = Math.min(lastTouchY, eventY);
            dirtyRect.bottom = Math.max(lastTouchY, eventY);
        }
    }
    //endregion


    //check version
    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    @TargetApi(23)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.CALL_PHONE"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }
}
