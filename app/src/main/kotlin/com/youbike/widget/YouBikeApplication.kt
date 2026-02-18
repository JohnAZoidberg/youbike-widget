package com.youbike.widget

import android.app.Application
import com.youbike.widget.worker.WidgetUpdateWorker

class YouBikeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetUpdateWorker.schedule(this)
    }
}
