package com.nenbucy.colorosdrawerhook.xposed

import com.nenbucy.colorosdrawerhook.ConfigStore
import com.nenbucy.colorosdrawerhook.DrawerConfig
import de.robv.android.xposed.XSharedPreferences
import org.json.JSONArray

internal class XposedConfigProvider(
    private val log: (String) -> Unit
) {
    private var prefs: XSharedPreferences? = null
    private var cachedConfigVersion: Long = Long.MIN_VALUE
    private var cachedConfig: DrawerConfig = DrawerConfig.EMPTY
    private var cachedBaselinePackagesJson: String? = null
    private var cachedBaselinePackages: Set<String> = emptySet()

    fun init() {
        prefs = XSharedPreferences(
            ConfigStore.MODULE_PACKAGE,
            ConfigStore.PREF_NAME
        )
        log("initConfigReader done")
    }

    fun getConfig(): DrawerConfig {
        val prefs = prefs

        if (prefs == null) {
            if (cachedConfig.categories.isNotEmpty()) {
                return cachedConfig
            }

            val parsed = DrawerConfig.parse(ConfigStore.DEFAULT_JSON)
            cachedConfig = parsed
            cachedConfigVersion = Long.MIN_VALUE

            log("configPrefs is null, use default config, categories=${parsed.categories.size}")
            return parsed
        }

        runCatching {
            prefs.reload()
        }.onFailure {
            log("prefs.reload failed: ${it.javaClass.name}: ${it.message}")
        }

        val version = prefs.getLong(ConfigStore.KEY_CONFIG_VERSION, -1L)

        if (version == cachedConfigVersion && cachedConfig.categories.isNotEmpty()) {
            return cachedConfig
        }

        val jsonFromPrefs = prefs.getString(ConfigStore.KEY_CONFIG_JSON, null)
        val json = if (jsonFromPrefs.isNullOrBlank()) {
            ConfigStore.DEFAULT_JSON
        } else {
            jsonFromPrefs
        }

        var parsed = DrawerConfig.parse(json)

        if (parsed.categories.isEmpty()) {
            parsed = DrawerConfig.parse(ConfigStore.DEFAULT_JSON)
        }

        cachedConfigVersion = version
        cachedConfig = parsed

        log(
            "config loaded: version=$version, " +
                    "categories=${parsed.categories.size}, " +
                    "packageRules=${parsed.packageToCategory.size}"
        )

        return parsed
    }

    fun shouldPutNewAppToOther(packageName: String): Boolean {
        val prefs = prefs ?: return false

        val enabled = prefs.getBoolean(
            ConfigStore.KEY_AUTO_NEW_APPS_TO_OTHER,
            false
        )

        if (!enabled) {
            return false
        }

        return packageName !in getBaselinePackages(prefs)
    }

    fun isDebugLogEnabled(): Boolean = prefs?.getBoolean(
        ConfigStore.KEY_DEBUG_LOG_ENABLED,
        false
    ) ?: false

    private fun getBaselinePackages(
        prefs: XSharedPreferences
    ): Set<String> {
        val json = prefs.getString(
            ConfigStore.KEY_BASELINE_PACKAGES_JSON,
            null
        )

        if (json == cachedBaselinePackagesJson) {
            return cachedBaselinePackages
        }

        val parsed = linkedSetOf<String>()

        if (!json.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val packageName = array.optString(i).trim()
                    if (packageName.isNotBlank()) {
                        parsed.add(packageName)
                    }
                }
            }.onFailure {
                log(
                    "parse baseline packages failed: " +
                            "${it.javaClass.name}: ${it.message}"
                )
            }
        }

        cachedBaselinePackagesJson = json
        cachedBaselinePackages = parsed
        return parsed
    }
}