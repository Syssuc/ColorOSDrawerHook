package com.nenbucy.colorosdrawerhook.xposed

import com.nenbucy.colorosdrawerhook.ConfigStore
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Process
import androidx.core.content.ContextCompat

internal fun registerLauncherRestartBroadcastReceiver(
    context: Context,
    mainHandler: Handler,
    log: (String) -> Unit
): Boolean {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ConfigStore.ACTION_RESTART_LAUNCHER) {
                return
            }

            log("restart launcher requested")
            mainHandler.postDelayed(
                {
                    log("killing launcher process for config reload")
                    Process.killProcess(Process.myPid())
                },
                500L
            )
        }
    }
    val filter = IntentFilter(ConfigStore.ACTION_RESTART_LAUNCHER)

    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        log("restart launcher receiver registered")
        true
    }.getOrElse {
        log(
            "register restart launcher receiver failed: " +
                    "${it.javaClass.name}: ${it.message}"
        )
        false
    }
}