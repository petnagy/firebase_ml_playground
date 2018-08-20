package com.playground.firebase.ml.playground

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.SparseIntArray
import android.view.Surface
import android.widget.Toast
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark
import kotlinx.android.synthetic.main.vilicamera.*
import timber.log.Timber

class CameraActivityWithMoodDetection : AppCompatActivity() {

    companion object {
        private val ORIENTATIONS = SparseIntArray()
        private const val CAMERA_ID = "1"
        private const val CAMERA_REQUEST = 1888
        private const val CAMERA_PERMISSION_CODE = 100

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private var captureInProgress = false

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vilicamera)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onResume() {
        super.onResume()

        val options =
                FirebaseVisionFaceDetectorOptions.Builder()
                        .setModeType(FirebaseVisionFaceDetectorOptions.ACCURATE_MODE)
                        .setLandmarkType(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationType(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .setMinFaceSize(0.15f)
                        .setTrackingEnabled(true)
                        .build()

        button.setOnClickListener {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
            } else {
                val detector = FirebaseVision.getInstance().getVisionFaceDetector(options)
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                for (cameraId in cameraManager.cameraIdList) {
                    Timber.d("CAMERA_ID: $cameraId")
                }
                val handlerThread = HandlerThread("imageHandler")
                handlerThread.start()
                val cameraThread = HandlerThread("cameraThread")
                val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2)

                imageReader.setOnImageAvailableListener(fun(reader: ImageReader) {
                    val originalImage = reader.acquireLatestImage() ?: return
                    if (!captureInProgress) {
                        Timber.d("create media image")
                        captureInProgress = true
                        val image = FirebaseVisionImage.fromMediaImage(originalImage, getRotationCompensation(this@CameraActivityWithMoodDetection, cameraManager, CAMERA_ID))
                        detector.detectInImage(image)
                                .addOnSuccessListener(fun(firebaseVisionfaces: List<FirebaseVisionFace>?) {
                                    if (firebaseVisionfaces?.isEmpty() == true) {
                                        if (captureInProgress) {
                                            captureInProgress = false
                                        }
                                        return
                                    }
                                    processFaces(firebaseVisionfaces)
                                    Timber.d("DETECTED ${firebaseVisionfaces?.size.toString()}")
                                })
                                .addOnFailureListener(fun(p0: Exception) {
                                    Timber.d("DETECTED ERROR $p0")
                                })
                    }
                    originalImage.close()
                }, Handler(handlerThread.looper))
                cameraThread.start()
                val cameraHandler = Handler(cameraThread.looper)
                cameraManager.openCamera(CAMERA_ID, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice?) {
                        val surface = Surface(vili_texture.surfaceTexture)
                        camera?.createCaptureSession(mutableListOf(surface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigureFailed(session: CameraCaptureSession?) {
                                Timber.d("createCaptureSession: Unable to configure camera ")
                            }

                            override fun onConfigured(session: CameraCaptureSession?) {
                                Timber.d("onConfigured")
                                val captureRequestBuilder = camera.createCaptureRequest(TEMPLATE_RECORD)
                                captureRequestBuilder.addTarget(surface)
                                captureRequestBuilder.addTarget(imageReader.surface)
                                session?.setRepeatingRequest(captureRequestBuilder.build(), null, cameraHandler)
                            }
                        }, cameraHandler)
                    }

                    override fun onDisconnected(camera: CameraDevice?) {
                        Timber.d("onDisconnected: Camera with id: ${camera?.id} has been disconnected")
                    }

                    override fun onError(camera: CameraDevice?, error: Int) {
                        Timber.d("onError: Error code: $error received for camera: ${camera?.id}")
                    }

                }, cameraHandler)
            }
        }
    }

    private fun processFaces(firebaseVisionfaces: List<FirebaseVisionFace>?) {
        for (face in firebaseVisionfaces!!) {
            val bounds = face.boundingBox
            val rotY = face.headEulerAngleY  // Head is rotated to the right rotY degrees
            val rotZ = face.headEulerAngleZ  // Head is tilted sideways rotZ degrees

            // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
            // nose available):
            val leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR)
            if (leftEar != null) {
                val leftEarPos = leftEar.position
            }

            // If classification was enabled:
            if (face.smilingProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                val smileProb = face.smilingProbability

                if (smileProb > 0.3) {
                    Timber.d("Smile")
                } else {
                    Timber.d("No smile")
                }
                Timber.d("SMILE_DETECTED $smileProb")
            }
            if (face.rightEyeOpenProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                val rightEyeOpenProb = face.rightEyeOpenProbability
            }

            // If face tracking was enabled:
            if (face.trackingId != FirebaseVisionFace.INVALID_ID) {
                val id = face.trackingId
            }
        }
        if (captureInProgress) {
            captureInProgress = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show()
                val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, CAMERA_REQUEST)
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show()
            }

        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraAccessException::class)
    private fun getRotationCompensation(activity: Activity, cameraManager: CameraManager, cameraId: String): Int {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        val deviceRotation = activity.windowManager.defaultDisplay.rotation
        var rotationCompensation = ORIENTATIONS.get(deviceRotation)

        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        val sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360

        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        val result: Int
        when (rotationCompensation) {
            0 -> result = FirebaseVisionImageMetadata.ROTATION_0
            90 -> result = FirebaseVisionImageMetadata.ROTATION_90
            180 -> result = FirebaseVisionImageMetadata.ROTATION_180
            270 -> result = FirebaseVisionImageMetadata.ROTATION_270
            else -> {
                result = FirebaseVisionImageMetadata.ROTATION_0
                Timber.e("Bad rotation value: $rotationCompensation")
            }
        }
        return result
    }
}