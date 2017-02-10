package com.smartdialer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.nfc.Tag;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.opencv.ml.*;
import org.opencv.utils.Converters;

import static org.opencv.core.CvType.*;

public class CallActivity extends AppCompatActivity {

    private String currentPhotoPath;
    private ImageView imageView;

    private int imgX = 25;
    private int imgY = 35;

    private String cNumber = "";
    private EditText editText;

    private ArrayList<Integer> images = new ArrayList<Integer>();

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Bundle extras = getIntent().getExtras();
        AppMode mode = null;
        if(extras != null) {
            currentPhotoPath = extras.getString("imagePath");
            mode = (AppMode) extras.getSerializable("mode");
        }
        imageView = (ImageView)findViewById(R.id.imageView);
        setPic();

        addImages();

        if(mode.equals(AppMode.DRAWING))
            processImageDrawing();
        else
            processPhoto();

        editText = (EditText)findViewById(R.id.textView);
        editText.setText(cNumber);
    }

    private void setPic(){
        int targetW = imageView.getWidth();
        int targetH = imageView.getHeight();

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(currentPhotoPath, bmOptions);

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        int scaleFactor = 1;
        if((targetW > 0) || (targetH > 0)){
            scaleFactor = Math.min(photoW/targetW, photoH/targetH);
        }

        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);

        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
    }

    public void callNumber(View view){
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        cNumber = editText.getText().toString();
        String cNumberToCall = cNumber.replaceAll("/", "").replaceAll("-", "").replaceAll("\\+", "00");

        callIntent.setData(Uri.parse("tel:" + cNumberToCall));

        if(ActivityCompat.checkSelfPermission(CallActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED){
            return;
        }
        startActivity(callIntent);
    }

    public void cancel(View view){
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        startActivity(mainActivityIntent);
    }

    public void resizeImg(Mat img, Mat dest){
        int h = img.height();
        int w = img.width();
        int hw;

        if(h >= w)
            hw = h;
        else
            hw = w;

        Mat res = Mat.zeros(hw, hw, CV_8U);
        Mat resSub = res.submat((hw-h)/2,(hw+h)/2, (hw-w)/2,(hw+w)/2);

        resSub = img;

        Size s = new Size();
        s.width = imgX;
        s.height = imgY;
        Imgproc.resize(img, dest, s, 0, 0, Imgproc.INTER_CUBIC);
    }

    //region processing of image from drawing
    public void processImageDrawing(){
        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);

        Mat trainArray = Mat.zeros(images.size() * 20, imgX * imgY, CV_32F);
        int i = 0;
        for(int image: images){
            Mat one = null;
            try {
                one = Utils.loadResource(this, image);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Mat grayImg = new Mat();
            Imgproc.cvtColor(one, grayImg, Imgproc.COLOR_BGR2GRAY);
            Scalar s = new Scalar(255);
            Mat temp = new Mat(grayImg.rows(), grayImg.cols(), CV_8U, s);
            Core.subtract(temp, grayImg, grayImg);

            Mat grayOriginal = new Mat();
            grayImg.copyTo(grayOriginal);

            Mat threshImg = new Mat();
            Imgproc.threshold(grayImg, threshImg, 127, 255, 0);
            Mat kernel = Mat.ones(10, 10, CV_8U);
            Mat thresh = new Mat();
            Imgproc.dilate(threshImg, thresh, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            Imgproc.findContours(thresh, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            for(MatOfPoint cont: contours){
                MatOfPoint num = new MatOfPoint();
                cont.copyTo(num);

                Rect rect = Imgproc.boundingRect(num);
                Mat forResize = grayOriginal.submat(rect);

                if(rect.width > 8 && rect.height > 8){
                    Mat resized = new Mat();
                    resizeImg(forResize, resized);

                    Mat resizedTemp = resized.reshape(0, imgX * imgY);
                    Mat transposedResized = new Mat();
                    Core.transpose(resizedTemp, transposedResized);
                    transposedResized.convertTo(trainArray.row(i), CV_32F);
                    i++;
                }
            }
        }

        List<Integer> numLabels = new ArrayList<Integer>();

        for(int j = 0; j < 12; j++){
            for(int k = 0; k < (images.size() * 20) / 12; k++){
                numLabels.add(j);
            }
        }

        KNearest knn = KNearest.create();
        knn.train(trainArray, Ml.ROW_SAMPLE, Converters.vector_int_to_Mat(numLabels));


        Mat currentImg = new Mat();
        Utils.bitmapToMat(bitmap, currentImg);

        Mat grayCurrentImg = new Mat();
        Imgproc.cvtColor(currentImg, grayCurrentImg, Imgproc.COLOR_BGR2GRAY);
        Scalar s = new Scalar(255);
        Mat temp = new Mat(grayCurrentImg.rows(), grayCurrentImg.cols(), CV_8U, s);
        Core.subtract(temp, grayCurrentImg, grayCurrentImg);
        Mat grayCurrentOriginal = new Mat();
        grayCurrentImg.copyTo(grayCurrentOriginal);

        Mat threshCurrentImg = new Mat();
        Imgproc.threshold(grayCurrentImg, threshCurrentImg, 127, 255, 0);

        Mat kernel = Mat.ones(10, 10, CV_8U);

        Mat threshCurrent = new Mat();
        Imgproc.dilate(threshCurrentImg, threshCurrent, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

        List<MatOfPoint> contoursCurrent = new ArrayList<MatOfPoint>();
        Imgproc.findContours(threshCurrent, contoursCurrent, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        TreeMap<Integer, String> chars = new TreeMap<Integer, String>();
        for(MatOfPoint cont: contoursCurrent){
            MatOfPoint num = new MatOfPoint();
            cont.copyTo(num);

            Rect rect = Imgproc.boundingRect(num);
            Mat forResize = grayCurrentOriginal.submat(rect);

            if(rect.width > 8 && rect.height > 8){
                if((rect.width / rect.height) > 1.2){
                    chars.put(rect.x, "[12]");
                }

                else {
                    Mat resized = new Mat();
                    resizeImg(forResize, resized);

                    Mat resizedForKnn = new Mat();
                    Mat r = resized.reshape(0, imgX * imgY);
                    r.convertTo(resizedForKnn, CV_32F);

                    Mat result = new Mat();
                    Mat transposed = new Mat();
                    Core.transpose(resizedForKnn, transposed);
                    knn.findNearest(transposed, 4, result);
                    chars.put(rect.x, result.dump());
                }
            }
        }

        for(Integer key : chars.keySet()){
            if(chars.get(key).equals("[10]"))
                cNumber += "+";
            else if(chars.get(key).equals("[11]"))
                cNumber += "/";
            else if(chars.get(key).equals("[12]"))
                cNumber += "-";
            else
                cNumber += chars.get(key);
        }

        cNumber = cNumber.replaceAll("\\[", "").replaceAll("\\]", "");
    }
    //endregion

    //region processing of taken photo
    public void processPhoto(){
        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);

        Mat trainArray = Mat.zeros(images.size() * 20, imgX * imgY, CV_32F);
        int i = 0;

        //dataset for knn training
        for(int image : images){
            Mat img = null;
            try{
                img = Utils.loadResource(this, image);
            }catch(Exception e){
                e.printStackTrace();
            }

            Mat grayImg = new Mat();
            Imgproc.cvtColor(img, grayImg, Imgproc.COLOR_BGR2GRAY);
            Scalar s = new Scalar(255);
            Mat temp = new Mat(grayImg.rows(), grayImg.cols(), CV_8U, s);
            Core.subtract(temp, grayImg, grayImg);

            Mat grayOriginal = new Mat();
            grayImg.copyTo(grayOriginal);

            Mat threshImg = new Mat();
            Imgproc.threshold(grayImg, threshImg, 127, 255, 0);

            Mat kernel = Mat.ones(10, 10, CV_8U);
            Mat thresh = new Mat();
            Imgproc.dilate(threshImg, thresh, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            Imgproc.findContours(thresh, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            for(MatOfPoint cont : contours){
                MatOfPoint num = new MatOfPoint();
                cont.copyTo(num);

                Rect rect = Imgproc.boundingRect(num);
                Mat forResize = grayOriginal.submat(rect);

                if(rect.width > 8 && rect.height > 8){
                    Mat resized = new Mat();
                    resizeImg(forResize, resized);

                    Mat resizedTemp = resized.reshape(0, imgX * imgY);
                    Mat transposedResized = new Mat();
                    Core.transpose(resizedTemp, transposedResized);
                    transposedResized.convertTo(trainArray.row(i), CV_32F);

                    i++;
                }
            }
        }

        //labels for knn training
        List<Integer> numLabels = new ArrayList<Integer>();
        for(int j = 0; j < 12; j++){
            for(int k = 0; k < (images.size() * 20) / 12; k++){
                numLabels.add(j);
            }
        }

        //train knn
        KNearest knn = KNearest.create();
        knn.train(trainArray, Ml.ROW_SAMPLE, Converters.vector_int_to_Mat(numLabels));

        //processing photo
        Mat currentImg = new Mat();
        Utils.bitmapToMat(bitmap, currentImg);

        Mat resizedCurrent = new Mat();
        Size size = new Size();
        size.width = 900;
        size.height = 400;
        Imgproc.resize(currentImg, resizedCurrent, size, 0, 0, Imgproc.INTER_CUBIC);

        Mat grayCurrentImg = new Mat();
        Imgproc.cvtColor(resizedCurrent, grayCurrentImg, Imgproc.COLOR_BGR2GRAY);

        Mat grayCurrentOriginal = new Mat();
        grayCurrentImg.copyTo(grayCurrentOriginal);

        Mat threshCurrentImgTemp = new Mat();
        Imgproc.adaptiveThreshold(grayCurrentImg, threshCurrentImgTemp, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 8);
        Mat threshCurrentImg = new Mat();
        Imgproc.threshold(threshCurrentImgTemp, threshCurrentImg, 127, 255, 0);

        Mat kernel = Mat.ones(3, 3, CV_8U);
        Mat threshCurrent = new Mat();
        Scalar scalar = new Scalar(255);
        Mat temporary = new Mat(threshCurrentImg.rows(), threshCurrentImg.cols(), CV_8U, scalar);
        Core.subtract(temporary, threshCurrentImg, threshCurrentImg);
        Imgproc.dilate(threshCurrentImg, threshCurrent, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));

        List<MatOfPoint> contoursCurrent = new ArrayList<MatOfPoint>();
        Imgproc.findContours(threshCurrent, contoursCurrent, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        TreeMap<Integer, String> chars = new TreeMap<Integer, String>();
        for(MatOfPoint cont : contoursCurrent){
            MatOfPoint num = new MatOfPoint();
            cont.copyTo(num);

            Rect rect = Imgproc.boundingRect(num);
            Mat forResize = grayCurrentOriginal.submat(rect);
            Scalar s = new Scalar(255);
            Mat temp = new Mat(forResize.rows(), forResize.cols(), CV_8U, s);
            Core.subtract(temp, forResize, forResize);

            if((rect.width > 10 && rect.height > 10) || rect.width/rect.height > 3){
                if((rect.width / rect.height) > 1.2){
                    chars.put(rect.x, "[12]");
                }else{
                    Mat resized = new Mat();
                    resizeImg(forResize, resized);

                    Mat resizedForKnn = new Mat();
                    Mat resizedReshaped = resized.reshape(0, imgX * imgY);
                    resizedReshaped.convertTo(resizedForKnn, CV_32F);

                    Mat result = new Mat();
                    Mat transposed = new Mat();
                    Core.transpose(resizedForKnn, transposed);
                    knn.findNearest(transposed, 3, result);
                    chars.put(rect.x, result.dump());
                }
            }
        }

        for(Integer key : chars.keySet()){
            if(chars.get(key).equals("[10]"))
                cNumber += "+";
            else if(chars.get(key).equals("[11]"))
                cNumber += "/";
            else if(chars.get(key).equals("[12]"))
                cNumber += "-";
            else
                cNumber += chars.get(key);
        }

        cNumber = cNumber.replaceAll("\\[", "").replaceAll("\\]", "");
    }
    //endregion

    public void addImages(){
        images.add(R.drawable.zero_training);
        images.add(R.drawable.zero_training2);
        images.add(R.drawable.zero_training3);
        images.add(R.drawable.zero_training4);
        images.add(R.drawable.zero_training5);
        images.add(R.drawable.zero_training6);
        images.add(R.drawable.zero_training7);
        images.add(R.drawable.one_training);
        images.add(R.drawable.one_training2);
        images.add(R.drawable.one_training3);
        images.add(R.drawable.one_training4);
        images.add(R.drawable.one_training5);
        images.add(R.drawable.one_training6);
        images.add(R.drawable.one_training7);
        images.add(R.drawable.two_training);
        images.add(R.drawable.two_training2);
        images.add(R.drawable.two_training3);
        images.add(R.drawable.two_training4);
        images.add(R.drawable.two_training5);
        images.add(R.drawable.two_training6);
        images.add(R.drawable.two_training7);
        images.add(R.drawable.three_training);
        images.add(R.drawable.three_training2);
        images.add(R.drawable.three_training3);
        images.add(R.drawable.three_training4);
        images.add(R.drawable.three_training5);
        images.add(R.drawable.three_training6);
        images.add(R.drawable.three_training7);
        images.add(R.drawable.four_training);
        images.add(R.drawable.four_training2);
        images.add(R.drawable.four_training3);
        images.add(R.drawable.four_training4);
        images.add(R.drawable.four_training5);
        images.add(R.drawable.four_training6);
        images.add(R.drawable.four_training7);
        images.add(R.drawable.five_training);
        images.add(R.drawable.five_training2);
        images.add(R.drawable.five_training3);
        images.add(R.drawable.five_training4);
        images.add(R.drawable.five_training5);
        images.add(R.drawable.five_training6);
        images.add(R.drawable.five_training7);
        images.add(R.drawable.six_training);
        images.add(R.drawable.six_training2);
        images.add(R.drawable.six_training3);
        images.add(R.drawable.six_training4);
        images.add(R.drawable.six_training5);
        images.add(R.drawable.six_training6);
        images.add(R.drawable.six_training7);
        images.add(R.drawable.seven_training);
        images.add(R.drawable.seven_training2);
        images.add(R.drawable.seven_training3);
        images.add(R.drawable.seven_training4);
        images.add(R.drawable.seven_training5);
        images.add(R.drawable.seven_training6);
        images.add(R.drawable.seven_training7);
        images.add(R.drawable.eight_training);
        images.add(R.drawable.eight_training2);
        images.add(R.drawable.eight_training3);
        images.add(R.drawable.eight_training4);
        images.add(R.drawable.eight_training5);
        images.add(R.drawable.eight_training6);
        images.add(R.drawable.eight_training7);
        images.add(R.drawable.nine_training);
        images.add(R.drawable.nine_training2);
        images.add(R.drawable.nine_training3);
        images.add(R.drawable.nine_training4);
        images.add(R.drawable.nine_training5);
        images.add(R.drawable.nine_training6);
        images.add(R.drawable.nine_training7);
        images.add(R.drawable.plus_training);
        images.add(R.drawable.plus_training2);
        images.add(R.drawable.plus_training3);
        images.add(R.drawable.plus_training4);
        images.add(R.drawable.plus_training5);
        images.add(R.drawable.plus_training6);
        images.add(R.drawable.plus_training7);
        images.add(R.drawable.slash_training);
        images.add(R.drawable.slash_training2);
        images.add(R.drawable.slash_training3);
        images.add(R.drawable.slash_training4);
        images.add(R.drawable.slash_training5);
        images.add(R.drawable.slash_training6);
        images.add(R.drawable.slash_training7);
    }
}
