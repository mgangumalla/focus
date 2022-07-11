package com.walmart.focus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
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
import com.walmart.focus.databinding.ActivityMainBinding
import com.walmart.focus.viewmodel.MainViewModel
import com.walmart.focus.viewmodel.MainViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private var bitmap: Bitmap? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityMainBinding

    private lateinit var viewModel: MainViewModel

    companion object {
        const val REQUEST_CODE_CAMERA_PERMISSION = 100
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = MainViewModel()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermission()

        binding.captureButton.setOnClickListener { binding.viewFinder.bitmap?.let { setViewAndDetect(it) } }
        binding.retry.setOnClickListener { reset() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        viewModel.detectionResultList.observe(this) {
            bitmap?.let { capturedBitMap -> drawDetectionResult(capturedBitMap, it) }
        }

        return super.onCreateView(name, context, attrs)
    }

    // Draw the detection result on the bitmap and show it.
    private fun drawDetectionResult(capturedBitMap: Bitmap,
        it: Pair<String, ArrayList<DetectionResult>>) {
        val imgWithResult = viewModel.drawDetectionResult(capturedBitMap, it.second)
        runOnUiThread {
            binding.capturedPhoto.setImageBitmap(imgWithResult)
        }

        binding.classificationLabelView.visibility = View.VISIBLE
        binding.classificationLabel.text =
            it.first.ifEmpty { "No Classification Available" }
        Log.i(TAG, "classification_label: ${it.first}")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            /*
             *  Todo: Add Image Analyzer and move object detection there.
             */
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
            this@MainActivity.bitmap = bitmap
            viewModel.runObjectDetection(bitmap)
        }
    }
}

/**
 * DetectionResult
 *      A class to store the visualization info of a detected object.
 */
data class DetectionResult(val boundingBox: Rect, val text: String)
