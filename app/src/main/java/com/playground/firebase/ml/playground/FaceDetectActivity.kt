package com.playground.firebase.ml.playground

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.view.View
import kotlinx.android.synthetic.main.base_layout.*
import timber.log.Timber
import java.io.File
import java.io.IOException

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
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                //Create the File
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Timber.e(e,"Error")
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(this,
                            "com.example.android.fileprovider"
                            , it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
                }
            }
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
            val photo = data?.extras?.get("data") as Bitmap
            takePhoto.visibility = View.GONE
            image.visibility = View.VISIBLE
            val screenBitmap = convertBitmap(photo)
            image.setImageBitmap(screenBitmap)
//            getFaceTask(photo).addOnCompleteListener { task ->
//                resultText += processFacesResult(task)
//                response.text = resultText
//            }.continueWithTask { _ ->
//                getLabelingTask(photo).addOnCompleteListener { task ->
//                    resultText += processLabelingResult(task)
//                    response.text = resultText
//                }
//            }.continueWithTask { _ ->
//                getTextTask(photo).addOnCompleteListener { task ->
//                    resultText += processTextResult(task)
//                    response.text = resultText
//                }
//            }.continueWithTask { _ ->
//                getBarcodeTask(photo).addOnCompleteListener { task ->
//                    resultText += processBarcodeResult(task)
//                    response.text = resultText
//                }
//            }
        }
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