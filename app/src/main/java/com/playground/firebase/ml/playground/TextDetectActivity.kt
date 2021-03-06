package com.playground.firebase.ml.playground

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.base_layout.*
import pl.aprilapps.easyphotopicker.DefaultCallback
import pl.aprilapps.easyphotopicker.EasyImage
import timber.log.Timber
import java.io.File

class TextDetectActivity: AppCompatActivity() {

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 21431
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.face_detect_layout)

        takePhoto.setOnClickListener { checkAndLaunchCamera() }

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

        EasyImage.handleActivityResult(requestCode, resultCode, data, this, object: DefaultCallback() {
            override fun onImagePicked(imageFile: File?, source: EasyImage.ImageSource?, type: Int) {
                if (imageFile != null) {
                    takePhoto.visibility = View.GONE
                    image.visibility = View.VISIBLE
                    val bitmap = loadBitmapFromCamera(image, imageFile.absolutePath)
                    if(bitmap != null) {
                        getTextTask(bitmap)
                    }
                }
            }

            override fun onImagePickerError(e: Exception?, source: EasyImage.ImageSource?, type: Int) {
                Timber.e(e, "Exception!!!!!")
            }

            override fun onCanceled(source: EasyImage.ImageSource?, type: Int) {
                //Cancel handling, you might wanna remove taken photo if it was canceled
                if (source == EasyImage.ImageSource.CAMERA) {
                    EasyImage.lastlyTakenButCanceledPhoto(this@TextDetectActivity)?.delete()
                }
            }
        })
    }

    private fun getTextTask(bitmap: Bitmap): Task<FirebaseVisionText> {
        val visionImage = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer
        return detector.processImage(visionImage).addOnSuccessListener { text ->
            val recognizedText = text.textBlocks.joinToString { block -> block.text }
            Timber.d("SUCCESS and text: $recognizedText")

            val paint = Paint()
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5F
            val tempBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
            val tempCanvas = Canvas(tempBitmap)
            tempCanvas.drawBitmap(bitmap, 0.0F, 0.0F, Paint())

            text.textBlocks.forEach { block ->
                block.boundingBox?.let { boundingBox ->
                    tempCanvas.drawRect(boundingBox, paint)
                }
            }

            image.setImageBitmap(tempBitmap)

        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
        }
    }

}