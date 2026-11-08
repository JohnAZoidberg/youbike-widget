package me.danielschaefer.android.youbike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.danielschaefer.android.youbike.worker.WidgetUpdateWorker

/**
 * Debug builds only: triggers a widget update from adb, also while the device is locked.
 * Only callers holding DUMP (i.e. adb shell) can send the broadcast. See README "Debugging".
 */
class DebugRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdateWorker.runOnce(context)
    }
}
