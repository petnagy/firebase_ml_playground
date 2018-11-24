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
import com.google.firebase.ml.vision.cloud.FirebaseVisionCloudDetectorOptions
import com.google.firebase.ml.vision.cloud.label.FirebaseVisionCloudLabel
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmark
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.base_layout.*
import pl.aprilapps.easyphotopicker.DefaultCallback
import pl.aprilapps.easyphotopicker.EasyImage
import timber.log.Timber
import java.io.File


class RemoteMlActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 1234
        const val CAMERA_REQUEST_CODE = 34242
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.base_layout)

        progress.visibility = View.INVISIBLE
        takePhoto.setOnClickListener { checkAndLaunchCamera() }

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
                        showProgress()
                        getLandmarkTask(bitmap).addOnCompleteListener { task ->
                            resultText += processLandmarksResult(task)
                            response.text = resultText
                        }.continueWithTask {
                            getLabelingTask(bitmap).addOnCompleteListener { task ->
                                resultText += processLabelingResult(task)
                                response.text = resultText
                            }
                        }.continueWithTask {
                            getTextTask(bitmap).addOnCompleteListener { task ->
                                resultText += processTextResult(task)
                                response.text = resultText
                                hideProgress()
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
                    EasyImage.lastlyTakenButCanceledPhoto(this@RemoteMlActivity)?.delete()
                }
            }
        })

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {

        }
    }

    private fun processTextResult(task: Task<FirebaseVisionText>): String {
        var result = ""
        val visionText = task.result
        val recognizedText = visionText?.text ?: ""
        if (recognizedText.isNotEmpty()) {
            result += "Text: [$recognizedText]"
            result += "\n"
        }
        return result
    }

    private fun processLabelingResult(task: Task<List<FirebaseVisionCloudLabel>>): String {
        var result = ""
        task.result?.let { labels ->
            if (labels.isNotEmpty()) {
                result += "Labels: "
                labels.forEach { label ->
                    result += "[${label.label}] "
                }
                result += "\n"
            }
        }
        return result
    }

    private fun processLandmarksResult(task: Task<List<FirebaseVisionCloudLandmark>>): String {
        var result = ""
        task.result?.let { landmarks ->
            if (landmarks.isNotEmpty()) {
                result += "Landmark detection: "
                landmarks.forEach { landmark ->
                    result += "[${landmark.landmark}] "
                }
                result += "\n"
            }
        }
        return result
    }

    private fun getLandmarkTask(bitmap: Bitmap): Task<List<FirebaseVisionCloudLandmark>> {
        val options = FirebaseVisionCloudDetectorOptions.Builder()
                .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
                .setMaxResults(15)
                .build()
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getVisionCloudLandmarkDetector(options)
        return detector.detectInImage(image).addOnSuccessListener { list ->
            Timber.d("SUCCESS and size of landmarks: ${list.size}")
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
            hideProgress()
        }
    }

    private fun getLabelingTask(bitmap: Bitmap): Task<List<FirebaseVisionCloudLabel>> {
        val options = FirebaseVisionCloudDetectorOptions.Builder()
                .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
                .setMaxResults(15)
                .build()
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getVisionCloudLabelDetector(options)
        return detector.detectInImage(image).addOnSuccessListener { list ->
            Timber.d("SUCCESS and size of labels: ${list.size}")
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
            hideProgress()
        }
    }

    private fun getTextTask(bitmap: Bitmap): Task<FirebaseVisionText> {
        val options = FirebaseVisionCloudTextRecognizerOptions.Builder()
                .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
                .build()
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getCloudTextRecognizer(options)
        return detector.processImage(image).addOnSuccessListener { text ->
            Timber.d("SUCCESS and text: ${text?.text}")
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
            hideProgress()
        }
    }

    private fun launchCamera() {
        Timber.d("Launch Camera")
        EasyImage.openCamera(this, 0)
        response.text = ""
    }

    private fun showProgress() {
        progress.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        progress.visibility = View.GONE
    }
}