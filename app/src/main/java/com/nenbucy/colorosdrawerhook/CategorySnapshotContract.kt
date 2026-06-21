package com.nenbucy.colorosdrawerhook

import android.net.Uri

object CategorySnapshotContract {

    const val AUTHORITY =
        "com.nenbucy.colorosdrawerhook.categorysnapshot"

    val URI: Uri =
        Uri.parse("content://$AUTHORITY")

    const val METHOD_SAVE =
        "saveSnapshot"

    const val METHOD_GET =
        "getSnapshot"

    const val KEY_JSON =
        "snapshot_json"

    const val PREF_NAME =
        "system_category_snapshot"

    const val PREF_KEY =
        "snapshot_json"
}