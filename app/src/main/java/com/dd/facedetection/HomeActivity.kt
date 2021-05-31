package com.dd.facedetection

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
    }

    fun onClickFaceDetection(view: View) {
        startActivity(Intent(this, FaceDetectionActivity::class.java))
    }

    fun onClickImageClassificationStatic(view: View) {
        startActivity(Intent(this, ImageClassificationStaticActivity::class.java))
    }

    fun onClickImageClassificationCamera(view: View) {
        startActivity(Intent(this, ImageClassificationCameraActivity::class.java))
    }
}