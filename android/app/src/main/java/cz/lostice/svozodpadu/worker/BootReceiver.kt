package cz.lostice.svozodpadu.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM_TRIGGER = "cz.lostice.svozodpadu.ACTION_ALARM_TRIGGER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WasteNotificationWorker.schedule(context)
            }
            ACTION_ALARM_TRIGGER -> {
                WasteNotificationWorker.triggerImmediateCheck(context)
            }
        }
    }
}
