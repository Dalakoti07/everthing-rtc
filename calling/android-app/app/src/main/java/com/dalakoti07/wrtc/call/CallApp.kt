package com.dalakoti07.wrtc.call

import android.app.Application
import android.content.Context

class CallApp: Application() {

    companion object{
        private lateinit var application: Application

        fun getContext(): Context{
            return application
        }
    }

    override fun onCreate() {
        super.onCreate()
        application = this
    }

}