package com.walmart.focus.viewmodel

import android.graphics.*
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.walmart.focus.DetectionResult
import java.util.*
import kotlin.collections.ArrayList

class MainViewModel : ViewModel() {
    var detectionResultList = MutableLiveData<Pair<String, ArrayList<DetectionResult>>>()

    /**
     * runObjectDetection(bitmap: Bitmap)
     * TFLite Object Detection function
     */
    fun runObjectDetection(bitmap: Bitmap) {
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
                    var outputText = if (results.size > 0 && results[0].labels.size > 0) "Classified as: " else ""
                    val resultToDisplay = ArrayList<DetectionResult>()

                    for (detectedObject in results) {
                        if (detectedObject.labels.size > 0) {
                            outputText += if (outputText.endsWith(' ')) "" else ", "
                            outputText += detectedObject.labels[0].text.capitalize(Locale.ROOT).trim()
                            resultToDisplay.add(
                                DetectionResult(
                                    detectedObject.boundingBox,
                                    "${detectedObject.labels[0].text}, ${detectedObject.labels[0].confidence}%"
                                )
                            )
                        }
                    }
                    detectionResultList.value = Pair(outputText, resultToDisplay)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error while detecting and classifying objects", e)
        }
    }
    /**
     * drawDetectionResult(bitmap: Bitmap, detectionResults: List<DetectionResult>
     *      Draw a box around each objects and show the object's name.
     */
    fun drawDetectionResult(
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

    companion object {
        const val TAG = "MainViewModel"
        private const val MAX_FONT_SIZE = 80F
        private const val OD_TF_LITE = "cereal_model.tflite"
    }
}