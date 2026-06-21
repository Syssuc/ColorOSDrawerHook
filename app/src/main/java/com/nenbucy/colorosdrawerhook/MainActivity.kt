package com.nenbucy.colorosdrawerhook

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nenbucy.colorosdrawerhook.model.HIDDEN_DRAWER_PICKER_PACKAGES
import com.nenbucy.colorosdrawerhook.model.InstalledApp
import com.nenbucy.colorosdrawerhook.model.toPrettyJson
import com.nenbucy.colorosdrawerhook.ui.editor.ConfigEditorScreen
import com.nenbucy.colorosdrawerhook.ui.theme.ColorosDrawerHookTheme
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ColorOSDrawerHook"
    }

    private var configPrefs: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configPrefs = openSharedConfig()

        val currentJson = configPrefs?.getString(
            ConfigStore.KEY_CONFIG_JSON,
            ConfigStore.DEFAULT_JSON
        ) ?: ConfigStore.DEFAULT_JSON
        val systemCategoryJson = configPrefs?.getString(
            ConfigStore.KEY_SYSTEM_CATEGORY_JSON,
            null
        )
        val autoNewAppsToOther = configPrefs?.getBoolean(
            ConfigStore.KEY_AUTO_NEW_APPS_TO_OTHER,
            false
        ) ?: false
        val hideEmptySystemCategories = configPrefs?.getBoolean(
            ConfigStore.KEY_HIDE_EMPTY_SYSTEM_CATEGORIES,
            false
        ) ?: false
        val debugLogEnabled = configPrefs?.getBoolean(
            ConfigStore.KEY_DEBUG_LOG_ENABLED,
            false
        ) ?: false
        val baselinePackagesJson = configPrefs?.getString(
            ConfigStore.KEY_BASELINE_PACKAGES_JSON,
            null
        )

        debugLogInfo("config opened: " +
                    "prefsAvailable=${configPrefs != null}, " +
                    "version=${configPrefs?.getLong(ConfigStore.KEY_CONFIG_VERSION, -1L)}"
        )

        val initialConfig = DrawerConfig.parse(currentJson).let {
            if (it.categories.isEmpty()) {
                DrawerConfig.parse(ConfigStore.DEFAULT_JSON)
            } else {
                it
            }
        }

        setContent {
            ColorosDrawerHookTheme {
                ConfigEditorScreen(
                    initialConfig = initialConfig,
                    initialSystemCategoryPackages = parseSystemCategoryPackages(systemCategoryJson),
                    initialSystemAppTitles = parseSystemAppTitles(systemCategoryJson),
                    initialAutoNewAppsToOther = autoNewAppsToOther,
                    initialHideEmptySystemCategories = hideEmptySystemCategories,
                    initialDebugLogEnabled = debugLogEnabled,
                    initialBaselinePackages = parsePackageSet(baselinePackagesJson),
                    loadInstalledApps = ::loadInstalledApps,
                    loadAppsForPackages = ::loadAppsForPackages,
                    onAutoNewAppsToOtherChange = ::saveAutoNewAppsToOther,
                    onHideEmptySystemCategoriesChange = ::saveHideEmptySystemCategories,
                    onDebugLogEnabledChange = ::saveDebugLogEnabled,
                    onSave = ::saveConfig,
                    onRestartLauncher = ::requestLauncherRestart,
                    onReset = {
                        DrawerConfig.parse(ConfigStore.DEFAULT_JSON)
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun openSharedConfig(): SharedPreferences? {
        return try {
            getSharedPreferences(
                ConfigStore.PREF_NAME,
                Context.MODE_WORLD_READABLE
            )
        } catch (e: SecurityException) {
            debugLogError("MODE_WORLD_READABLE unavailable. Check xposedsharedprefs metadata and LSPosed activation.",
                e
            )

            Toast.makeText(
                this,
                "无法打开 LSPosed 共享配置，请检查 Manifest 和模块启用状态",
                Toast.LENGTH_LONG
            ).show()

            null
        }
    }

    private fun isDebugLogEnabled(): Boolean = configPrefs?.getBoolean(
        ConfigStore.KEY_DEBUG_LOG_ENABLED,
        false
    ) ?: false

    private fun debugLogInfo(message: String) {
        if (isDebugLogEnabled()) {
            Log.i(TAG, message)
        }
    }

    private fun debugLogError(message: String, throwable: Throwable) {
        if (isDebugLogEnabled()) {
            Log.e(TAG, message, throwable)
        }
    }
    private fun saveConfig(config: DrawerConfig): Boolean {
        val duplicateAliases = config.categories
            .groupingBy { it.alias }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        if (duplicateAliases.isNotEmpty()) {
            Toast.makeText(
                this,
                "分类内部标识重复：${duplicateAliases.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val emptyFields = config.categories.firstOrNull {
            it.alias.isBlank() || it.title.isBlank()
        }

        if (emptyFields != null) {
            Toast.makeText(
                this,
                "分类名称和内部标识不能为空",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val json = config.toPrettyJson()
        val parsed = DrawerConfig.parse(json)

        if (parsed.categories.isEmpty()) {
            Toast.makeText(
                this,
                "配置解析失败或分类为空",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        val prefs = configPrefs ?: openSharedConfig()

        if (prefs == null) {
            Toast.makeText(
                this,
                "共享配置不可用，无法保存",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        configPrefs = prefs

        val version = System.currentTimeMillis()

        val success = prefs.edit()
            .putString(ConfigStore.KEY_CONFIG_JSON, json)
            .putLong(ConfigStore.KEY_CONFIG_VERSION, version)
            .commit()

        val verifyVersion = prefs.getLong(ConfigStore.KEY_CONFIG_VERSION, -1L)
        val verifyJson = prefs.getString(ConfigStore.KEY_CONFIG_JSON, null)

        debugLogInfo("config save: " +
                    "success=$success, " +
                    "version=$version, " +
                    "verifyVersion=$verifyVersion, " +
                    "jsonLength=${verifyJson?.length ?: 0}, " +
                    "categories=${parsed.categories.size}, " +
                    "aliases=${parsed.categories.map { it.alias }}"
        )

        val verified = success && verifyVersion == version
        Toast.makeText(
            this,
            if (verified) {
                "保存成功"
            } else {
                "配置写入验证失败"
            },
            Toast.LENGTH_LONG
        ).show()

        return verified
    }

    private fun requestLauncherRestart(): Boolean {
        return runCatching {
            sendBroadcast(
                Intent(ConfigStore.ACTION_RESTART_LAUNCHER)
                    .setPackage("com.android.launcher")
            )
            true
        }.getOrElse {
            debugLogError("request launcher restart failed", it)
            Toast.makeText(
                this,
                "保存成功，但发送重启桌面请求失败",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }
    private fun loadInstalledApps(): List<InstalledApp> {
        val packageManager = packageManager

        return runCatching {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_ALL
            )
                .mapNotNull { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                    val packageName = activityInfo.packageName ?: return@mapNotNull null
                    val label = resolveInfo.loadLabel(packageManager).toString()

                    if (isHiddenFromDrawerPicker(label, packageName)) {
                        return@mapNotNull null
                    }

                    InstalledApp(
                        label = label,
                        packageName = packageName,
                        componentName = "${activityInfo.packageName}/${activityInfo.name}",
                        icon = resolveInfo.loadIcon(packageManager).toBitmap(
                            width = 96,
                            height = 96
                        )
                    )
                }
                .distinctBy { it.componentName }
                .sortedWith(
                    compareBy<InstalledApp> { it.label.lowercase() }
                        .thenBy { it.packageName }
                )
        }.getOrElse {
            debugLogError("load installed apps failed", it)
            emptyList()
        }
    }

    private fun loadAppsForPackages(
        packageNames: Set<String>,
        titleOverrides: Map<String, String>
    ): List<InstalledApp> {
        if (packageNames.isEmpty()) {
            return emptyList()
        }

        val packageManager = packageManager

        return packageNames.mapNotNull { packageName ->
            runCatching {
                val appInfo = packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_ALL
                )
                val label = titleOverrides[packageName]
                    ?: appInfo.loadLabel(packageManager).toString()

                if (isHiddenFromDrawerPicker(label, packageName)) {
                    return@mapNotNull null
                }

                InstalledApp(
                    label = label,
                    packageName = packageName,
                    componentName = packageName,
                    icon = appInfo.loadIcon(packageManager).toBitmap(
                        width = 96,
                        height = 96
                    )
                )
            }.getOrNull()
        }.sortedWith(
            compareBy<InstalledApp> { it.label.lowercase() }
                .thenBy { it.packageName }
        )
    }

    private fun isHiddenFromDrawerPicker(label: String, packageName: String): Boolean {
        val normalizedLabel = label
            .lowercase()
            .replace(" ", "")

        return packageName in HIDDEN_DRAWER_PICKER_PACKAGES ||
                normalizedLabel == "simtoolkit" ||
                normalizedLabel == "sim工具包" ||
                normalizedLabel.startsWith("sim卡") && normalizedLabel.endsWith("工具包")
    }

    private fun saveDebugLogEnabled(enabled: Boolean): Boolean {
        val prefs = configPrefs ?: openSharedConfig() ?: return false
        configPrefs = prefs

        return prefs.edit()
            .putBoolean(ConfigStore.KEY_DEBUG_LOG_ENABLED, enabled)
            .commit()
    }
    private fun saveHideEmptySystemCategories(enabled: Boolean): Boolean {
        val prefs = configPrefs ?: openSharedConfig() ?: return false
        configPrefs = prefs

        return prefs.edit()
            .putBoolean(ConfigStore.KEY_HIDE_EMPTY_SYSTEM_CATEGORIES, enabled)
            .commit()
    }
    private fun saveAutoNewAppsToOther(enabled: Boolean): Boolean {
        val prefs = configPrefs ?: openSharedConfig() ?: return false
        configPrefs = prefs

        val editor = prefs.edit()
            .putBoolean(ConfigStore.KEY_AUTO_NEW_APPS_TO_OTHER, enabled)

        if (enabled && prefs.getString(ConfigStore.KEY_BASELINE_PACKAGES_JSON, null).isNullOrBlank()) {
            val baseline = JSONArray()
            loadInstalledApps()
                .map { it.packageName }
                .distinct()
                .sorted()
                .forEach { baseline.put(it) }

            editor.putString(
                ConfigStore.KEY_BASELINE_PACKAGES_JSON,
                baseline.toString()
            )
        }

        return editor.commit()
    }

    private fun parseSystemCategoryPackages(
        json: String?
    ): Map<String, List<String>> {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val root = JSONObject(json)
            val categories = root.optJSONArray("categories")
                ?: return@runCatching emptyMap()
            val result = linkedMapOf<String, List<String>>()

            for (i in 0 until categories.length()) {
                val category = categories.optJSONObject(i) ?: continue
                val alias = category.optString("alias").trim()
                val packagesJson = category.optJSONArray("packages")
                val packages = mutableListOf<String>()

                if (alias.isBlank() || packagesJson == null) {
                    continue
                }

                for (j in 0 until packagesJson.length()) {
                    val packageName = packagesJson.optString(j).trim()
                    if (packageName.isNotBlank()) {
                        packages.add(packageName)
                    }
                }

                result[alias] = packages.distinct()
            }

            normalizeOtherCategory(result)
        }.getOrElse {
            debugLogError("parse system category snapshot failed", it)
            emptyMap()
        }
    }

    private fun parsePackageSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) {
            return emptySet()
        }

        return runCatching {
            val array = JSONArray(json)
            buildSet {
                for (i in 0 until array.length()) {
                    val packageName = array.optString(i).trim()
                    if (packageName.isNotBlank()) {
                        add(packageName)
                    }
                }
            }
        }.getOrElse {
            debugLogError("parse package set failed", it)
            emptySet()
        }
    }

    private fun parseSystemAppTitles(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return runCatching {
            val root = JSONObject(json)
            val categories = root.optJSONArray("categories")
                ?: return@runCatching emptyMap()
            val result = linkedMapOf<String, String>()

            for (i in 0 until categories.length()) {
                val category = categories.optJSONObject(i) ?: continue
                val apps = category.optJSONArray("apps") ?: continue

                for (j in 0 until apps.length()) {
                    val app = apps.optJSONObject(j) ?: continue
                    val packageName = app.optString("package").trim()
                    val title = app.optString("title").trim()

                    if (packageName.isNotBlank() && title.isNotBlank()) {
                        result[packageName] = title
                    }
                }
            }

            result
        }.getOrElse {
            debugLogError("parse system app titles failed", it)
            emptyMap()
        }
    }

    private fun normalizeOtherCategory(
        categories: Map<String, List<String>>
    ): Map<String, List<String>> {
        val packagesInSpecificCategories = categories
            .filterKeys { it != "other" && it != "suggestion" }
            .values
            .flatten()
            .toSet()

        return categories.mapValues { (alias, packages) ->
            if (alias == "other") {
                packages.filter { it !in packagesInSpecificCategories }
            } else {
                packages
            }
        }
    }
}
