package com.github.tvbox.osc.base

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private var instance: App? = null

        @JvmStatic
        fun getInstance(): App = instance
            ?: throw IllegalStateException("App not initialized")
    }
}
