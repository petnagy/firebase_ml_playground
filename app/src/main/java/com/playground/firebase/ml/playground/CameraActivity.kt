package com.playground.firebase.ml.playground

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import kotlinx.android.synthetic.main.camera_layout.*
import timber.log.Timber


class CameraActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 21431
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    var camera: CameraDevice? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var imageDimension: Size? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var session: CameraCaptureSession? = null
    private var cameraId: String = ""
    private var imageReader: ImageReader? = null
    private lateinit var detector: FirebaseVisionFaceDetector
    private var captureInProgress: Boolean = false
    private lateinit var manager: CameraManager

    private val textureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            // Transform you image captured size according to the surface width and height
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return false
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Timber.d("onSurfaceTextureAvailable")
            openCamera()
        }
    }

    private val stateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice?) {
            Timber.d("Opened")
            this@CameraActivity.camera = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice?) {
            this@CameraActivity.camera?.close()
        }

        override fun onError(camera: CameraDevice?, error: Int) {
            this@CameraActivity.camera?.close()
            this@CameraActivity.camera = null
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_layout)
        texture.surfaceTextureListener = textureListener
        drawing.isOpaque = true
        drawing.alpha = 0.5f
        val options = FirebaseVisionFaceDetectorOptions.Builder()
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build()
        detector = FirebaseVision.getInstance().getVisionFaceDetector(options)
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera Background")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Timber.e(e)
        }
    }

    private fun createCameraPreview() {
        try {
            val surfaceTexture = texture.surfaceTexture
            surfaceTexture.setDefaultBufferSize(imageDimension!!.width, imageDimension!!.height)
            val surface = Surface(surfaceTexture)

            val handlerThread = HandlerThread("imageHandler")
            handlerThread.start()
            imageReader = ImageReader.newInstance(1440, 2112, ImageFormat.YUV_420_888, 30)
            imageReader?.setOnImageAvailableListener(fun(reader: ImageReader) {
                Timber.d("ImageReader ready")
                val image = reader.acquireLatestImage() ?: return
                Timber.d("Image Size: ${image.width}, ${image.height}")
                val imageSize = Size(image.width, image.height)
                if (!captureInProgress) {
                    Timber.d("Image process start")
                    captureInProgress = true
                    val firebaseImage = FirebaseVisionImage.fromMediaImage(image, getRotationCompensation(this@CameraActivity, manager, cameraId))
                    detector.detectInImage(firebaseImage).addOnSuccessListener { faceList ->
                        Timber.d("face detected: ${faceList.size}")

                        if (faceList.isNotEmpty()) {
                            faceList.forEach { faceVision ->
                                Timber.d("Bounding box: ${faceVision.boundingBox}")
                                drawRect(faceVision.boundingBox, imageSize)
                            }
                        } else {
                            //TODO clear canvas
                        }

                        if (captureInProgress) {
                            captureInProgress = false
                        }
                    }.addOnFailureListener { exception ->
                        Timber.e(exception, "Exception")
                        if (captureInProgress) {
                            captureInProgress = false
                        }
                    }
                }
                image.close()
            }, Handler(handlerThread.looper))
            captureRequestBuilder = camera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)
            captureRequestBuilder?.addTarget(imageReader?.surface)
            camera?.createCaptureSession(listOf(surface, imageReader?.surface), object: CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession?) {
                    Timber.d("Configuration change")
                }

                override fun onConfigured(session: CameraCaptureSession?) {
                    Timber.d("onConfigured")
                    //The camera is already closed
                    if (camera == null) {
                        return
                    }
                    // When the session is ready, we start displaying the preview.
                    this@CameraActivity.session = session
                    updatePreview()
                }

            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    private fun drawRect(rect: Rect, imageSize: Size) {
        val canvas = drawing.lockCanvas()
        canvas?.let{ canvas ->
            Timber.d("Canvas width: ${canvas.width} and height: ${canvas.height}")
            val paint = Paint()
            paint.color = Color.WHITE
            val widthCorrection = canvas.width / imageSize.width.toDouble()
            val heightCorrection = canvas.height / imageSize.height.toDouble()
            Timber.d("Corrections: $widthCorrection, $heightCorrection")
            val fixedRect = Rect((rect.left * widthCorrection).toInt(), (rect.top * heightCorrection).toInt(),
                    (rect.right * widthCorrection).toInt(), (rect.bottom * heightCorrection).toInt())

            Timber.d("fixed Rect: $fixedRect")
            canvas.drawRect(fixedRect, paint)
            drawing.unlockCanvasAndPost(canvas)
        }
    }

    private fun openCamera() {
        //val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Timber.d("Camera is opening")
        try {
            cameraId = manager.cameraIdList[0]
            if (manager.cameraIdList.size > 1) {
                cameraId = manager.cameraIdList[1]
            }
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), CAMERA_PERMISSION_REQUEST_CODE)
                return
            }
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    private fun updatePreview() {
        Timber.d("update Preview")
        if (camera == null) {
            Timber.e("updatePreview error, return")
        }
        captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            session?.setRepeatingRequest(captureRequestBuilder?.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    private fun closeCamera() {
        if (camera != null) {
            camera?.close()
            camera = null
        }
        if (imageReader != null) {
            imageReader?.close()
            imageReader = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        startBackgroundThread()
        if (texture.isAvailable) {
            openCamera()
        } else {
            texture.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Timber.d("onPause")
        stopBackgroundThread()
        closeCamera()
        super.onPause()
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
        Timber.d("Rotation: $result")
        return result
    }
}