package com.nenbucy.colorosdrawerhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

class SystemCategorySnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConfigStore.ACTION_SYSTEM_CATEGORY_SNAPSHOT) {
            return
        }

        val json = intent.getStringExtra(ConfigStore.EXTRA_SYSTEM_CATEGORY_JSON)
            ?: return

        runCatching {
            JSONObject(json)

            @Suppress("DEPRECATION")
            val prefs = context.getSharedPreferences(
                ConfigStore.PREF_NAME,
                Context.MODE_WORLD_READABLE
            )

            prefs.edit()
                .putString(ConfigStore.KEY_SYSTEM_CATEGORY_JSON, json)
                .putLong(
                    ConfigStore.KEY_SYSTEM_CATEGORY_VERSION,
                    System.currentTimeMillis()
                )
                .commit()
        }.onFailure {
            Log.e("ColorOSDrawerHook", "save system category snapshot failed", it)
        }
    }
}
