package com.playground.firebase.ml.playground

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import kotlinx.android.synthetic.main.camera_layout.*
import timber.log.Timber


class CameraActivity : AppCompatActivity() {

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 21431
    }

    private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }

    var camera: CameraDevice? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var imageDimension: Size? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var session: CameraCaptureSession? = null
    private var cameraId: String = ""
    private var imageReader: ImageReader? = null

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

    private val captureCallbackListener = object: CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {
            super.onCaptureCompleted(session, request, result)
            createCameraPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_layout)
        texture.surfaceTextureListener = textureListener
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
            captureRequestBuilder = camera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)
            camera?.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession?) {
                    Timber.d("Configuration change")
                }

                override fun onConfigured(session: CameraCaptureSession?) {
                    //The camera is already closed
                    if (camera == null) {
                        return
                    }
                    // When the session is ready, we start displaying the preview.
                    this@CameraActivity.session = session
                    updatePreview()
                }

            }, null)
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Timber.d("Camera is openning")
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
        super.onPause()
    }
}