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
    }

    private fun startFirebaseRemoteMl() {
        Timber.d("Start Remote ML")
        startActivity(Intent(this@MainActivity, RemoteMlActivity::class.java))
    }

    private fun startFirebaseLocalMl() {
        Timber.d("Start Local ML")
        startActivity(Intent(this@MainActivity, LocalMlActivity::class.java))
    }
}
