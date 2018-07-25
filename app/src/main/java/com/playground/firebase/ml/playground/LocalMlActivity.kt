package com.playground.firebase.ml.playground

import android.Manifest.permission.CAMERA
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.google.firebase.ml.vision.label.FirebaseVisionLabel
import com.google.firebase.ml.vision.label.FirebaseVisionLabelDetectorOptions
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.base_layout.*
import timber.log.Timber


class LocalMlActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_PERMisSION_REQUEST_CODE = 21431
        const val CAMERA_REQUEST_CODE = 1535
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.base_layout)

        progress.visibility = View.INVISIBLE
        takePhoto.setOnClickListener { _ -> checkAndLaunchCamera() }
    }

    private fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE), CAMERA_PERMisSION_REQUEST_CODE)
        } else {
            launchCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMisSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            var resultText = ""
            response.text = resultText
            val photo = data?.extras?.get("data") as Bitmap
            image.setImageBitmap(photo)
            getFaceTask(photo).addOnCompleteListener { task ->
                resultText += processFacesResult(task)
                response.text = resultText
            }.continueWithTask { _ ->
                getLabelingTask(photo).addOnCompleteListener { task ->
                    resultText += processLabelingResult(task)
                    response.text = resultText
                }
            }.continueWithTask { _ ->
                getTextTask(photo).addOnCompleteListener { task ->
                    resultText += processTextResult(task)
                    response.text = resultText
                }
            }.continueWithTask { _ ->
                getBarcodeTask(photo).addOnCompleteListener { task ->
                    resultText += processBarcodeResult(task)
                    response.text = resultText
                }
            }
        }
    }

    private fun processBarcodeResult(task: Task<List<FirebaseVisionBarcode>>): String {
        var result = ""
        val barcodes = task.result
        if (barcodes.isNotEmpty()) {
            result += "Barcode: " + barcodes.map { barcode -> barcode.rawValue }.joinToString(prefix = "[", postfix = "]")
            result += "\n"
        }
        return result
    }

    private fun processTextResult(task: Task<FirebaseVisionText>): String {
        var result = ""
        val visionText = task.result
        val recognizedText = visionText.blocks.joinToString { block -> block.text }
        if (recognizedText.isNotEmpty()) {
            result += "Text: [$recognizedText]"
        }
        result += "\n"
        return result
    }

    private fun processLabelingResult(task: Task<List<FirebaseVisionLabel>>): String {
        var result = ""
        val labels = task.result
        if (labels.isNotEmpty()) {
            result += "Labels: "
            labels.forEach { label ->
                result += "[${label.label}] "
            }
            result += "\n"
        }
        return result
    }

    private fun processFacesResult(task: Task<List<FirebaseVisionFace>>): String {
        var result = ""
        val faces = task.result
        if (faces.isNotEmpty()) {
            result += "Face detection: "
            faces.forEach { face ->
                result += "[smile ${face.smilingProbability}] "
            }
            result += "\n"
        }
        return result
    }

    private fun getFaceTask(bitmap: Bitmap): Task<List<FirebaseVisionFace>> {
        val options = FirebaseVisionFaceDetectorOptions.Builder()
                .setModeType(FirebaseVisionFaceDetectorOptions.ACCURATE_MODE)
                .setLandmarkType(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationType(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .setMinFaceSize(0.15f)
                .setTrackingEnabled(false)
                .build()
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getVisionFaceDetector(options)
        return detector.detectInImage(image).addOnSuccessListener { list ->
            Timber.d("SUCCESS and size of faces: ${list.size}")
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
        }
    }

    private fun getLabelingTask(bitmap: Bitmap): Task<List<FirebaseVisionLabel>> {
        val options = FirebaseVisionLabelDetectorOptions.Builder()
                .setConfidenceThreshold(0.8f)
                .build()
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getVisionLabelDetector(options)
        return detector.detectInImage(image).addOnSuccessListener { list ->
            Timber.d("SUCCESS and size of labels: ${list.size}")
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
        }
    }

    private fun getTextTask(bitmap: Bitmap): Task<FirebaseVisionText> {
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().visionTextDetector
        return detector.detectInImage(image).addOnSuccessListener { text ->
            val recognizedText = text.blocks.joinToString { block -> block.text }
            Timber.d("SUCCESS and text: $recognizedText")
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
        }
    }

    private fun getBarcodeTask(bitmap: Bitmap): Task<List<FirebaseVisionBarcode>> {
        val options = FirebaseVisionBarcodeDetectorOptions.Builder()
                .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_ALL_FORMATS)
                .build()
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options)
        return detector.detectInImage(image).addOnSuccessListener { barcodes ->
            Timber.d("SUCCESS and size of labels: ${barcodes.size}")
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
        }
    }

    private fun launchCamera() {
        Timber.d("Launch Camera")
        startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), CAMERA_REQUEST_CODE)
        response.text = ""
    }

}