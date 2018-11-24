package com.playground.firebase.ml.playground

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import kotlinx.android.synthetic.main.base_layout.*
import pl.aprilapps.easyphotopicker.DefaultCallback
import pl.aprilapps.easyphotopicker.EasyImage
import timber.log.Timber
import java.io.File

class FaceDetectActivity : AppCompatActivity() {

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

        EasyImage.handleActivityResult(requestCode, resultCode, data, this, object:DefaultCallback() {
            override fun onImagePicked(imageFile: File?, source: EasyImage.ImageSource?, type: Int) {
                if (imageFile != null) {
                    takePhoto.visibility = View.GONE
                    image.visibility = View.VISIBLE
                    val bitmap = loadBitmapFromCamera(image, imageFile.absolutePath)
                    if(bitmap != null) {
                        getFaceTask(bitmap)
                    }
                }
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
    }

    private fun getFaceTask(bitmap: Bitmap): Task<List<FirebaseVisionFace>> {
        val options = FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build()
        val firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().getVisionFaceDetector(options)
        return detector.detectInImage(firebaseVisionImage).addOnSuccessListener { list ->
            Timber.d("SUCCESS and size of faces: ${list.size}")

            val framePaint = Paint()
            framePaint.color = Color.WHITE
            framePaint.style = Paint.Style.STROKE
            framePaint.strokeWidth = 4f

            val textPaint = Paint()
            textPaint.color = Color.WHITE
            textPaint.style = Paint.Style.FILL_AND_STROKE
            textPaint.textSize = 30f
            textPaint.strokeWidth = 2f

            val tempBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
            val tempCanvas = Canvas(tempBitmap)
            tempCanvas.drawBitmap(bitmap, 0.0F, 0.0F, Paint())

            list.forEach { faceVision ->
                tempCanvas.drawRect(faceVision.boundingBox, framePaint)
                val textX: Float = calculateX(faceVision.boundingBox)
                val textY: Float = calculateY(faceVision.boundingBox)
                tempCanvas.drawText("%.2f".format(faceVision.smilingProbability), textX, textY, textPaint)
            }

            image.setImageBitmap(tempBitmap)
        }.addOnFailureListener { exception ->
            Timber.e("FAILED ${exception.localizedMessage}")
        }
    }

    private fun calculateX(rect: Rect): Float {
        return rect.exactCenterX()
    }

    private fun calculateY(rect: Rect): Float {
        return (rect.bottom - 20).toFloat()
    }
}