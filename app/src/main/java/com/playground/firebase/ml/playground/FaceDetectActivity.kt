package com.playground.firebase.ml.playground

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import kotlinx.android.synthetic.main.base_layout.*
import pl.aprilapps.easyphotopicker.DefaultCallback
import pl.aprilapps.easyphotopicker.EasyImage
import timber.log.Timber
import java.io.File

class FaceDetectActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 21431
        const val CAMERA_REQUEST_CODE = 1535
    }

    lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.face_detect_layout)

        takePhoto.setOnClickListener { _ -> checkAndLaunchCamera() }

        EasyImage.clearConfiguration(this)
        EasyImage.configuration(this)
                .setImagesFolderName("Images_Playground")
                .saveInAppExternalFilesDir()
    }

    private fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), LocalMlActivity.CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        Timber.d("Launch Camera")
        //startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), CAMERA_REQUEST_CODE)

        EasyImage.openCamera(this, 0)
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

            }

            override fun onImagePickerError(e: Exception?, source: EasyImage.ImageSource?, type: Int) {
                Timber.e(e, "Exception!!!!!")
            }

            override fun onCanceled(source: EasyImage.ImageSource?, type: Int) {
                //Cancel handling, you might wanna remove taken photo if it was canceled
                if (source == EasyImage.ImageSource.CAMERA) {
                    EasyImage.lastlyTakenButCanceledPhoto(this@FaceDetectActivity)?.delete()
                }
            }
        })

//        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
//            val photo = data?.extras?.get("data") as Bitmap
//            takePhoto.visibility = View.GONE
//            image.visibility = View.VISIBLE
//            val screenBitmap = convertBitmap(photo)
//            image.setImageBitmap(screenBitmap)
////            getFaceTask(photo).addOnCompleteListener { task ->
////                resultText += processFacesResult(task)
////                response.text = resultText
////            }.continueWithTask { _ ->
////                getLabelingTask(photo).addOnCompleteListener { task ->
////                    resultText += processLabelingResult(task)
////                    response.text = resultText
////                }
////            }.continueWithTask { _ ->
////                getTextTask(photo).addOnCompleteListener { task ->
////                    resultText += processTextResult(task)
////                    response.text = resultText
////                }
////            }.continueWithTask { _ ->
////                getBarcodeTask(photo).addOnCompleteListener { task ->
////                    resultText += processBarcodeResult(task)
////                    response.text = resultText
////                }
////            }
//        }
    }

    private fun convertBitmap(bitmap: Bitmap): Bitmap {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = bitmap.width
        val height = bitmap.height
        val scaleWidth = metrics.scaledDensity
        val scaleHeight = metrics.scaledDensity
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun createImageFile() : File {
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("image",".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }
}