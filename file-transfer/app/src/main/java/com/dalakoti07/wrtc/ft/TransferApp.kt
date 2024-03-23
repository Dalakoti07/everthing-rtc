package com.dalakoti07.wrtc.ft

import android.app.Application
import android.content.Context

class TransferApp: Application() {

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