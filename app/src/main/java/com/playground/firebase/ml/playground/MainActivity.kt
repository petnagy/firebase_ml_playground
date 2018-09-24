package com.playground.firebase.ml.playground

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        remote.setOnClickListener { _ -> startFirebaseRemoteMl() }
        local.setOnClickListener { _ -> startFirebaseLocalMl() }
        face_detect.setOnClickListener { _ -> startFaceDetect() }
        text_detection.setOnClickListener { _ -> startTextDetect() }
        //camera.setOnClickListener {_ -> startLiveCamera()}
        //camera2.setOnClickListener {_ -> startLiveCamera2()}
    }

    private fun startFirebaseRemoteMl() {
        Timber.d("Start Remote ML")
        startActivity(Intent(this@MainActivity, RemoteMlActivity::class.java))
    }

    private fun startFirebaseLocalMl() {
        Timber.d("Start Local ML")
        startActivity(Intent(this@MainActivity, LocalMlActivity::class.java))
    }

    private fun startFaceDetect() {
        Timber.d("Face detect start")
        startActivity(Intent(this@MainActivity, FaceDetectActivity::class.java))
    }

    private fun startTextDetect() {
        Timber.d("Face detect start")
        startActivity(Intent(this@MainActivity, TextDetectActivity::class.java))
    }

    private fun  startLiveCamera() {
        Timber.d("Start live Camera")
        startActivity(Intent(this@MainActivity, CameraActivity::class.java))
    }

    private fun  startLiveCamera2() {
        Timber.d("Start live Camera")
        startActivity(Intent(this@MainActivity, CameraActivityWithMoodDetection::class.java))
    }
}
