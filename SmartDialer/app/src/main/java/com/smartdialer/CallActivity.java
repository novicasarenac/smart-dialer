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
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opencv.ml.*;
import org.opencv.utils.Converters;

import static org.opencv.core.CvType.*;

public class CallActivity extends AppCompatActivity {

    private String currentPhotoPath;
    private ImageView imageView;

    private int imgX = 25;
    private int imgY = 35;

    private String cNumber = "";

    private ArrayList<Integer> images = new ArrayList<Integer>();

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        } else {
            //System.loadLibrary("my_jni_lib1");
            //System.loadLibrary("my_jni_lib2");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Bundle extras = getIntent().getExtras();
        if(extras != null)
            currentPhotoPath = extras.getString("imagePath");

        imageView = (ImageView)findViewById(R.id.imageView);
        setPic();

        addImages();



        processImage();
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
        callIntent.setData(Uri.parse("tel:00381642364832"));

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
        //res2.submat((hw-h)/2,(hw+h)/2, (hw-w)/2,(hw+w)/2);
        //res2 = img;
        //res2[(hw-h)/2,(hw+h)/2, (hw-w)/2,(hw+w)/2] = img;
        /*(h, w) = obj_img.shape
                hw = max(w, h)
        res2 = np.zeros((hw, hw), np.uint8)
        res2[(hw-h)/2:(hw+h)/2, (hw-w)/2:(hw+w)/2] = obj_img
        return cv2.resize(res2, (imgX, imgY), interpolation=cv2.INTER_CUBIC)*/
    }

    public void processImage(){
        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);

        Mat trainArray = Mat.zeros(images.size() * 20, imgX * imgY, CV_32F);
        int i = 0;
        for(int image: images){
            Mat one = null;
            try {
                //int id = this.getResources().getIdentifier(image,"drawable", this.getPackageName());
                one = Utils.loadResource(this, image);
                //one = R.drawable.zero_training;
                //one = Imgcodecs.imread("drawable\\" + image);
                //one = Utils.loadResource(getApplicationContext(), getApplicationContext().getResources().getIdentifier(image, "drawable", getPackageName()), CV_8U);
            } catch (Exception e) {
                e.printStackTrace();
            }

            int sad = one.width();
            Mat grayImg = new Mat();
            Imgproc.cvtColor(one, grayImg, Imgproc.COLOR_BGR2GRAY);
            Scalar s = new Scalar(255);
            Mat temp = new Mat(grayImg.rows(), grayImg.cols(), CV_8U, s);
            Core.subtract(temp, grayImg, grayImg);

            //grayImg = 255 - grayImg;
            //Scalar s = new Scalar(255);
            //grayImg = s - grayImg.;
            //grayImg += (integer)250;
            Mat grayOriginal = new Mat();
            grayImg.copyTo(grayOriginal);

            Mat threshImg = new Mat();
            Imgproc.threshold(grayImg, threshImg, 127, 255, 0);

            Mat kernel = Mat.ones(10, 10, CV_8U);

            Mat thresh = new Mat();
            //Imgproc.dilate(threshImg, thresh, kernel, 0, 1);
            Imgproc.dilate(threshImg, thresh, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            Imgproc.findContours(thresh, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            for(MatOfPoint cont: contours){
                MatOfPoint num = new MatOfPoint();
                cont.copyTo(num);

                Rect rect = Imgproc.boundingRect(num);
                Mat forResize = grayOriginal.submat(rect);
                //num.subm


                if(rect.width > 8 && rect.height > 8){
                    Mat resized = new Mat();
                    resizeImg(forResize, resized);

                    Mat r = resized.reshape(0, imgX * imgY);
                    r.convertTo(trainArray.row(i), CV_32F);
                    //resized.reshape(-1, imgX * imgY).convertTo(trainArray.row(i), CV_32FC1);
                    //train_array[i] = resized.reshape(-1, img_x*img_y).astype(np.float32)

                    i++;
                }
            }
        }

        List<Integer> numLabels = new ArrayList<Integer>();
        //for(int j = 0; j < 12; j++)
          //  numLabels.add(j);

        for(int j = 0; j < 12; j++){
            for(int k = 0; k < 40; k++){
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
        //grayImg = 255 - grayImg;
        //Scalar s = new Scalar(255);
        //grayImg = s - grayImg.;
        //grayImg += (integer)250;
        Mat grayCurrentOriginal = new Mat();
        grayCurrentImg.copyTo(grayCurrentOriginal);

        Mat threshCurrentImg = new Mat();
        Imgproc.threshold(grayCurrentImg, threshCurrentImg, 127, 255, 0);

        Mat kernel = Mat.ones(10, 10, CV_8U);

        Mat threshCurrent = new Mat();
        //Imgproc.dilate(threshImg, thresh, kernel, 0, 1);
        Imgproc.dilate(threshCurrentImg, threshCurrent, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

        List<MatOfPoint> contoursCurrent = new ArrayList<MatOfPoint>();
        Imgproc.findContours(threshCurrent, contoursCurrent, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        ArrayMap<Integer, String> chars = new ArrayMap<Integer, String>();
        for(MatOfPoint cont: contoursCurrent){
            MatOfPoint num = new MatOfPoint();
            cont.copyTo(num);

            Rect rect = Imgproc.boundingRect(num);
            Mat forResize = grayCurrentOriginal.submat(rect);

            if(rect.width > 8 && rect.height > 8){
                if((rect.width / rect.height) > 1.2){
                    chars.put(rect.x, "12");
                }

                else {
                    Mat resized = new Mat();
                    resizeImg(forResize, resized);

                    Mat resizedForKnn = new Mat();
                    //resized.reshape(0, imgX * imgY).convertTo(resizedForKnn, CV_32FC1);
                    Mat r = resized.reshape(0, imgX * imgY);
                    r.convertTo(resizedForKnn, CV_32F);

                    Mat result = new Mat();
                    Mat rrrr = new Mat();
                    Core.transpose(resizedForKnn, rrrr);
                    knn.findNearest(rrrr, 4, result);
                    chars.put(rect.x, result.dump());
                }
            }
        }

        for(Map.Entry<Integer, String> entry : chars.entrySet()){
            if(entry.getValue() == "10")
                cNumber += "+";
            else if(entry.getValue() == "11")
                cNumber += "/";
            else if(entry.getValue() == "12")
                cNumber += "-";
            else
                cNumber += entry.getValue();
        }

        String sa = cNumber;
    }

    public void addImages(){
        images.add(R.drawable.zero_training);
        images.add(R.drawable.zero_training2);
        images.add(R.drawable.one_training);
        images.add(R.drawable.one_training2);
        images.add(R.drawable.two_training);
        images.add(R.drawable.two_training2);
        images.add(R.drawable.three_training);
        images.add(R.drawable.three_training2);
        images.add(R.drawable.four_training);
        images.add(R.drawable.four_training2);
        images.add(R.drawable.five_training);
        images.add(R.drawable.five_training2);
        images.add(R.drawable.six_training);
        images.add(R.drawable.six_training2);
        images.add(R.drawable.seven_training);
        images.add(R.drawable.seven_training2);
        images.add(R.drawable.eight_training);
        images.add(R.drawable.eight_training2);
        images.add(R.drawable.nine_training);
        images.add(R.drawable.nine_training2);
        images.add(R.drawable.plus_training);
        images.add(R.drawable.plus_training2);
        images.add(R.drawable.slash_training);
        images.add(R.drawable.slash_training2);
        /*images.add("zero_training.jpeg");
        images.add("zero_training2.jpeg");
        images.add("one_training.jpeg");
        images.add("one_training2.jpeg");
        images.add("two_training.jpeg");
        images.add("two_training2.jpeg");
        images.add("three_training.jpeg");
        images.add("three_training2.jpeg");
        images.add("four_training.jpeg");
        images.add("four_training2.jpg");
        images.add("five_training.jpeg");
        images.add("five_training2.jpg");
        images.add("six_training.jpeg");
        images.add("six_training2.jpeg");
        images.add("seven_training.jpeg");
        images.add("seven_training2.jpg");
        images.add("eight_training.jpeg");
        images.add("eight_training2.jpg");
        images.add("nine_training.jpeg");
        images.add("nine_training2.jpeg");
        images.add("plus_training.jpeg");
        images.add("plus_training2.jpeg");
        images.add("slash_training.jpeg");
        images.add("slash_training2.jpeg");*/

        /*images = ["zero_training.jpeg", "zero_training2.jpeg", "one_training.jpeg", "one_training2.jpeg", "two_training.jpeg",
                "two_training2.jpeg", "three_training.jpeg", "three_training2.jpeg", "four_training.jpeg",
                "four_training2.jpg",
                "five_training.jpeg", "five_training2.jpg", "six_training.jpeg", "six_training2.jpeg", "seven_training.jpeg",
                "seven_training2.jpg", "eight_training.jpeg", "eight_training2.jpg", "nine_training.jpeg",
                "nine_training2.jpeg",
                "plus_training.jpeg", "plus_training2.jpeg", "slash_training.jpeg", "slash_training2.jpeg"]*/
    }
}
