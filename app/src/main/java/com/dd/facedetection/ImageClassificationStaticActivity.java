package com.dd.facedetection;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.IOException;
import java.util.List;

public class ImageClassificationStaticActivity extends AppCompatActivity {

    public static final String MODEL_NAME = "model.tflite";
    protected TextView tvMessage;
    private String TAG = ImageClassificationStaticActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_classification_static);
        tvMessage = findViewById(R.id.tvMessage);
        ImageClassifier.ImageClassifierOptions options = ImageClassifier
                .ImageClassifierOptions.builder().setMaxResults(1).build();
        ImageClassifier imageClassifier = null;
        try {
            imageClassifier = ImageClassifier.createFromFileAndOptions(this, "model.tflite", options);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (imageClassifier == null) {
            Log.e(TAG, "[ImageClassifier] IS NULL");
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dasiy);
        TensorImage inputImage = TensorImage.fromBitmap(bitmap);
        assert imageClassifier != null;
        List<Classifications> results = imageClassifier.classify(inputImage);
        if (results != null && results.get(0) != null) {
            List<Category> categories = results.get(0).getCategories();
            if (categories != null && categories.get(0) != null) {
                String msg = categories.get(0).getLabel() + " " + categories.get(0).getScore();
                Log.i(TAG, msg);
                tvMessage.setText(msg);
            }
        }
    }
}