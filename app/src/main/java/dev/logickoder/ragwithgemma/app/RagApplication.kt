package dev.logickoder.ragwithgemma.app

import android.app.Application

class RagApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
