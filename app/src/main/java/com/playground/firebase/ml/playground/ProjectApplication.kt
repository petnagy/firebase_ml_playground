package com.playground.firebase.ml.playground

import android.app.Application
import timber.log.Timber.DebugTree
import timber.log.Timber



class ProjectApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(DebugTree())
    }

}