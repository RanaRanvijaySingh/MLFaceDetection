package com.dd.facedetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.SensorManager.getOrientation
import android.media.Image
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_face_detection.*
import kotlinx.android.synthetic.main.activity_image_classification_camera.*
import kotlinx.android.synthetic.main.activity_image_classification_camera.tvMessage
import kotlinx.android.synthetic.main.activity_image_classification_camera.viewFinder
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ImageClassificationCameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraXBasic"
        private const val JUMP = "Jump"
        private const val NO_JUMP = "No Jump"
        private const val ACCURACY_THRESHOLD = 0.90
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    private var isCounting: Boolean = false
    private var jumpCount: Int = 0
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    var imageClassifier: ImageClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_classification_camera)
        // Request camera permissions
        if (allPermissionsGranted()) {
            val options = ImageClassifier.ImageClassifierOptions.builder().setMaxResults(1).build()
            try {
                imageClassifier =
                    ImageClassifier.createFromFileAndOptions(this, "model.tflite", options)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun startCamera() {
        // Get screen metrics used to setup camera for full screen resolution
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        var width = displayMetrics.widthPixels
        var height = displayMetrics.heightPixels
        val screenAspectRatio = aspectRatio(width, height)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider = cameraProviderFuture.get()
            // Preview
            val preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }
            imageCapture = ImageCapture.Builder().build()
            val imageAnalyzer = ImageAnalysis.Builder().build()
            imageAnalyzer.setAnalyzer(cameraExecutor, LuminosityAnalyzer { imageProxy ->
                onAnalysis(imageProxy)
                imageProxy.close()
            })
            // Select front camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun onAnalysis(imageProxy: ImageProxy) {
        imageClassifier?.let { imageClassifier ->
            imageProxy.image?.let { image ->
                toBitmap(image)?.let { bitmap ->
                    val inputImage: TensorImage = TensorImage.fromBitmap(bitmap)
                    val width = bitmap.width
                    val height = bitmap.height
                    val cropSize = min(width, height)
                    val imageOptions: ImageProcessingOptions =
                        getTensorImageOption(width, cropSize, height)
                    val results: List<Classifications> = imageClassifier.classify(inputImage, imageOptions)
                    updateUI(results)
                }
            }
        }
    }

    private fun updateUI(results: List<Classifications>?) {
        results?.let { classifications ->
            classifications[0]?.let { classification ->
                classification.categories?.let { categories ->
                    val score = categories[0].score
                    val label = categories[0].label
                    if (label.equals(JUMP, true) && score >= ACCURACY_THRESHOLD) {
                        tvMessage.setBackgroundColor(resources.getColor(R.color.green))
                        if (isCounting) {
                            jumpCount++
                            runOnUiThread(Runnable { tvCount.text = jumpCount.toString() })
                        }
                    } /*else if (score < ACCURACY_THRESHOLD && score >= 0.6) {
                        tvMessage.setBackgroundColor(resources.getColor(R.color.yellow))
                    } */else {
                        tvMessage.setBackgroundColor(resources.getColor(R.color.red))
                    }
                    tvMessage.text = "${categories[0].label}: ${categories[0].score}"
                }
            }
        }
    }

    private fun getTensorImageOption(
        width: Int,
        cropSize: Int,
        height: Int
    ): ImageProcessingOptions {
        val imageOptions: ImageProcessingOptions =
            ImageProcessingOptions.builder()
//                .setOrientation(getOrientation(sensorOrientation)) // Set the ROI to the center of the image.
                .setRoi(
                    Rect( /*left=*/
                        (width - cropSize) / 2,  /*top=*/
                        (height - cropSize) / 2,  /*right=*/
                        (width + cropSize) / 2,  /*bottom=*/
                        (height + cropSize) / 2
                    )
                )
                .build()
        return imageOptions
    }

    private fun toBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val yBuffer: ByteBuffer = planes[0].buffer
        val uBuffer: ByteBuffer = planes[1].buffer
        val vBuffer: ByteBuffer = planes[2].buffer
        val ySize: Int = yBuffer.remaining()
        val uSize: Int = uBuffer.remaining()
        val vSize: Int = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage =
            YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes: ByteArray = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private class LuminosityAnalyzer(val listener: ImageAnalyzerListener) : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            listener(imageProxy)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    fun onClickReset(view: View) {
        isCounting = false
        jumpCount = 0
        tvCount.text = jumpCount.toString()
    }

    fun onClickStart(view: View) {
        isCounting = true
    }

    fun onClickStop(view: View) {
        isCounting = false
    }
}
