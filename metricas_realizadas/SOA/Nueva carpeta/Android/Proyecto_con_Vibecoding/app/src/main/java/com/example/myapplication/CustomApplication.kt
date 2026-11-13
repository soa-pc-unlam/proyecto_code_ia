package com.example.myapplication

import android.app.Application

class CustomApplication : Application() {
    lateinit var mqttManager: MqttManager

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager(this)
    }
}