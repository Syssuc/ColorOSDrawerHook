package com.nenbucy.colorosdrawerhook

object ConfigStore {
    const val MODULE_PACKAGE = "com.nenbucy.colorosdrawerhook"

    const val PREF_NAME = "drawer_config"
    const val KEY_CONFIG_JSON = "config_json"
    const val KEY_CONFIG_VERSION = "config_version"
    const val KEY_SYSTEM_CATEGORY_JSON = "system_category_json"
    const val KEY_SYSTEM_CATEGORY_VERSION = "system_category_version"
    const val KEY_AUTO_NEW_APPS_TO_OTHER = "auto_new_apps_to_other"
    const val KEY_HIDE_EMPTY_SYSTEM_CATEGORIES = "hide_empty_system_categories"
    const val KEY_DEBUG_LOG_ENABLED = "debug_log_enabled"
    const val KEY_BASELINE_PACKAGES_JSON = "baseline_packages_json"

    const val ACTION_SYSTEM_CATEGORY_SNAPSHOT =
        "com.nenbucy.colorosdrawerhook.action.SYSTEM_CATEGORY_SNAPSHOT"
    const val ACTION_RESTART_LAUNCHER =
        "com.nenbucy.colorosdrawerhook.action.RESTART_LAUNCHER"
    const val EXTRA_SYSTEM_CATEGORY_JSON = "system_category_json"

    val DEFAULT_JSON = """
        {
          "categories": [
            
          ]
        }
    """.trimIndent()
}
