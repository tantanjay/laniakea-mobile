package com.laniakea

import android.app.Application
import com.laniakea.di.AppContainer

class LaniakeaApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
