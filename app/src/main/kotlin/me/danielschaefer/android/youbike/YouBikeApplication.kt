package me.danielschaefer.android.youbike

import android.app.Application
import me.danielschaefer.android.youbike.worker.WidgetUpdateWorker

class YouBikeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetUpdateWorker.schedule(this)
    }
}
