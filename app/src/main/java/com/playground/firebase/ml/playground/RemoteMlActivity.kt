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
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmark
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import timber.log.Timber


class RemoteMlActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_PERMisSION_REQUEST_CODE = 1234
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
            val photo = data?.extras?.get("data") as Bitmap
            image.setImageBitmap(photo)
            val options = FirebaseVisionCloudDetectorOptions.Builder()
                    .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
                    .setMaxResults(15)
                    .build()
            val image = FirebaseVisionImage.fromBitmap(photo)
            val detector = FirebaseVision.getInstance().getVisionCloudLandmarkDetector(options)
            val result: Task<List<FirebaseVisionCloudLandmark>> = detector.detectInImage(image).addOnSuccessListener { list ->
                Timber.d("SUCCESS")
                Timber.d("size of list: %s", list.size)
                resultText += "Landmark detection: "
                if (list.isNotEmpty()) {
                    list.map { landmark ->
                        resultText += "[${landmark.landmark}] "
                    }
                }
                response.text = resultText
            }.addOnFailureListener{
                Timber.e("FAILED %s", it.localizedMessage)
            }
        }
    }

    private fun launchCamera() {
        Timber.d("Launch Camera")
        startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), CAMERA_REQUEST_CODE)
        response.text = ""
    }

}