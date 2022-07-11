package com.walmart.focus

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.walmart.focus.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val REQUEST_CODE_CAMERA_PERMISSION = 0
        const val TAG = "MainActivity"
        private const val MAX_FONT_SIZE = 96F
        private const val OD_TF_LITE = "cereal_model.tflite"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermission()

        binding.captureButton.setOnClickListener { binding.viewFinder.bitmap?.let { setViewAndDetect(it) } }
        binding.retry.setOnClickListener { reset() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll() // Unbind use cases before rebinding
                cameraProvider.bindToLifecycle(this, cameraSelector,
                    preview, imageCapture) // Bind use cases to camera
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this@MainActivity,
                arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA_PERMISSION)
        } else {
            Log.i(TAG, "Permission already granted")
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission already granted")
                startCamera()
            } else {
                Toast.makeText(this@MainActivity,
                    "Camera Permission Denied, can't use the app", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun reset() {
        binding.retry.visibility = View.GONE
        binding.viewFinder.visibility = View.VISIBLE
        binding.captureButton.visibility = View.VISIBLE
        binding.capturedPhoto.visibility = View.GONE
        binding.classificationLabelView.visibility = View.GONE
    }

    /**
     * setViewAndDetect(bitmap: Bitmap)
     * Set image to view and call object detection
     */
    private fun setViewAndDetect(bitmap: Bitmap, image: ImageProxy? = null) {
        binding.retry.visibility = View.VISIBLE
        binding.viewFinder.visibility = View.GONE
        binding.captureButton.visibility = View.INVISIBLE
        binding.capturedPhoto.visibility = View.VISIBLE
        Glide.with(this).load(bitmap).into(binding.capturedPhoto)
        image?.close()

        lifecycleScope.launch(Dispatchers.Default) {
            runObjectDetection(bitmap)
        }
    }

    /**
     * runObjectDetection(bitmap: Bitmap)
     * TFLite Object Detection function
     */
    private fun runObjectDetection(bitmap: Bitmap) {
        try {
            val localModel = LocalModel.Builder()
                .setAssetFilePath(OD_TF_LITE)
                .build()

            // Multiple object detection in static images
            val customObjectDetectorOptions =
                CustomObjectDetectorOptions.Builder(localModel)
                    .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .setClassificationConfidenceThreshold(0.5f)
                    .setMaxPerObjectLabelCount(3)
                    .build()

            // Initialize the detector object
            val objectDetector =
                ObjectDetection.getClient(customObjectDetectorOptions)

            // Feed given image to the detector
            // Parse the detection result and show it
            objectDetector
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error while detecting object", e)
                }.addOnSuccessListener { results ->
                    var outputText = ""
                    val resultToDisplay = ArrayList<DetectionResult>()

                    for (detectedObject in results) {
                        if (detectedObject.labels.size > 0) {
                            outputText += if (outputText.isNotEmpty()) "\n" else ""
                            outputText += "${detectedObject.labels[0].text} : ${detectedObject.labels[0].confidence}"
                            println("detectedObject.boundingBox: ${detectedObject.boundingBox}")
                            resultToDisplay.add(
                                DetectionResult(
                                    detectedObject.boundingBox,
                                    "${detectedObject.labels[0].text}, ${detectedObject.labels[0].confidence}%"
                                )
                            )
                        }
                    }

                    // Draw the detection result on the bitmap and show it.
                    val imgWithResult = drawDetectionResult(bitmap, resultToDisplay)
                    runOnUiThread {
                        binding.capturedPhoto.setImageBitmap(imgWithResult)
                    }

                    binding.classificationLabelView.visibility = View.VISIBLE
                    binding.classificationLabel.text =
                        outputText.ifEmpty { "No Classification Available" }
                    Log.i(TAG, "classification_label: $outputText")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error while detecting and classifying objects", e)
        }
    }

    /**
     * drawDetectionResult(bitmap: Bitmap, detectionResults: List<DetectionResult>
     *      Draw a box around each objects and show the object's name.
     */
    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<DetectionResult>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach {
            // draw bounding box
            pen.color = Color.RED
            pen.strokeWidth = 6F
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox
            canvas.drawRect(box, pen)

            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            pen.style = Paint.Style.FILL_AND_STROKE
            pen.color = Color.YELLOW
            pen.strokeWidth = 2F

            pen.textSize = MAX_FONT_SIZE
            pen.getTextBounds(it.text, 0, it.text.length, tagSize)
            val fontSize: Float = pen.textSize * box.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) pen.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                it.text, box.left + margin,
                box.top + tagSize.height().times(1F), pen
            )
        }
        return outputBitmap
    }
}

/**
 * DetectionResult
 *      A class to store the visualization info of a detected object.
 */
data class DetectionResult(val boundingBox: Rect, val text: String)
