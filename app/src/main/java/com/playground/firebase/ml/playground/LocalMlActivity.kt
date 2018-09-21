package com.playground.firebase.ml.playground

import android.Manifest.permission.CAMERA
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
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
import pl.aprilapps.easyphotopicker.DefaultCallback
import pl.aprilapps.easyphotopicker.EasyImage
import timber.log.Timber
import java.io.File


class LocalMlActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 21431
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.base_layout)

        progress.visibility = View.INVISIBLE
        takePhoto.setOnClickListener { _ -> checkAndLaunchCamera() }

        EasyImage.clearConfiguration(this)
        EasyImage.configuration(this)
                .setImagesFolderName("Images_Playground")
                .saveInAppExternalFilesDir()
    }

    private fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            launchCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        EasyImage.handleActivityResult(requestCode, resultCode, data, this, object:DefaultCallback() {
            override fun onImagePicked(imageFile: File?, source: EasyImage.ImageSource?, type: Int) {
                if (imageFile != null) {
                    val bitmap = loadBitmapFromCamera(image, imageFile.absolutePath)
                    if(bitmap != null) {
                        var resultText = ""
                        response.text = resultText
                        image.setImageBitmap(bitmap)
                        getFaceTask(bitmap).addOnCompleteListener { task ->
                            resultText += processFacesResult(task)
                            response.text = resultText
                        }.continueWithTask { _ ->
                            getLabelingTask(bitmap).addOnCompleteListener { task ->
                                resultText += processLabelingResult(task)
                                response.text = resultText
                            }
                        }.continueWithTask { _ ->
                            getTextTask(bitmap).addOnCompleteListener { task ->
                                resultText += processTextResult(task)
                                response.text = resultText
                            }
                        }.continueWithTask { _ ->
                            getBarcodeTask(bitmap).addOnCompleteListener { task ->
                                resultText += processBarcodeResult(task)
                                response.text = resultText
                            }
                        }
                    }
                }
            }

            override fun onImagePickerError(e: Exception?, source: EasyImage.ImageSource?, type: Int) {
                Timber.e(e, "Exception!!!!!")
            }

            override fun onCanceled(source: EasyImage.ImageSource?, type: Int) {
                //Cancel handling, you might wanna remove taken photo if it was canceled
                if (source == EasyImage.ImageSource.CAMERA) {
                    EasyImage.lastlyTakenButCanceledPhoto(this@LocalMlActivity)?.delete()
                }
            }
        })
    }

    private fun processBarcodeResult(task: Task<List<FirebaseVisionBarcode>>): String {
        var result = ""
        val barcodes = task.result
        if (barcodes.isNotEmpty()) {
            result += "Barcode: " + barcodes.asSequence().map { barcode -> barcode.rawValue }.joinToString(prefix = "[", postfix = "]")
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
        EasyImage.openCamera(this, 0)
        response.text = ""
    }

}