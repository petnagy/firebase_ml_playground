package com.playground.firebase.ml.playground

import android.Manifest.permission.CAMERA
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.base_layout.*
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.support.v4.content.ContextCompat
import android.view.View
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.cloud.FirebaseVisionCloudDetectorOptions
import com.google.firebase.ml.vision.cloud.label.FirebaseVisionCloudLabel
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmark
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import timber.log.Timber
import com.google.firebase.ml.vision.cloud.text.FirebaseVisionCloudText


class RemoteMlActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 1234
        const val CAMERA_REQUEST_CODE = 34242
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.base_layout)

        progress.visibility = View.INVISIBLE
        takePhoto.setOnClickListener { _ -> checkAndLaunchCamera() }
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
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            var resultText = ""
            response.text = resultText
            val photo = data?.extras?.get("data") as Bitmap
            image.setImageBitmap(photo)
            getLandmarkTask(photo).addOnCompleteListener { task ->
                resultText += processLandmarksResult(task)
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
            }
        }
    }

    private fun processTextResult(task: Task<FirebaseVisionCloudText>): String {
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

    private fun processLandmarksResult(task: Task<List<FirebaseVisionCloudLandmark>>): String {
        var result = ""
        val landmarks = task.result
        if (landmarks.isNotEmpty()) {
            result += "Landmark detection: "
            landmarks.forEach { landmark ->
                result += "[${landmark.landmark}] "
            }
            result += "\n"
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
        }
    }

    private fun getTextTask(bitmap: Bitmap): Task<FirebaseVisionCloudText> {
        val options = FirebaseVisionCloudDetectorOptions.Builder()
                .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
                .setMaxResults(15)
                .build()
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getVisionCloudTextDetector(options)
        return detector.detectInImage(image).addOnSuccessListener { text ->
            Timber.d("SUCCESS and text: ${text?.text}")
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