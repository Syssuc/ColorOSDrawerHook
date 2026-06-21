package com.nenbucy.colorosdrawerhook.xposed

import com.nenbucy.colorosdrawerhook.ConfigStore
import com.nenbucy.colorosdrawerhook.DrawerConfig
import android.app.Application
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XSharedPreferences
import org.json.JSONObject
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import org.json.JSONArray



class XposedEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private var latestNativeOrderMap: Map<String, Int>? = null
    private companion object {
        const val TARGET_PACKAGE = "com.android.launcher"
        const val CATEGORY_TITLE_CLASS = "com.android.launcher3.allapps.appcategory.AppCategoryType"

        const val NATIVE_ORDER_PREF_NAME = "category_order_prefs"
        const val NATIVE_ORDER_KEY = "item_order"
        const val EXTRA_ORIGINAL_APPS = "colorosdrawerhook_original_apps"
    }
    private val configProvider = XposedConfigProvider(::log)
    private var hasHookedLauncher = false
    private var restartReceiverRegistered = false

    private val officialCategoryAliases = setOf(
        "suggestion",
        "communicate",
        "tools",
        "photos",
        "entertainment",
        "shopping",
        "games",
        "travel",
        "health",
        "work",
        "finance",
        "education",
        "read",
        "carrier",
        "other"
    )

    private var launcherContext: Context? = null
    private val nativeCategoryPackages =
        linkedMapOf<String, MutableSet<String>>()
    private val nativeCategoryAppTitles =
        linkedMapOf<String, MutableMap<String, String>>()
    private var pendingSystemSnapshotBroadcast: Runnable? = null
    private var lastSystemSnapshotJson: String? = null

    private val mainHandler by lazy {
        android.os.Handler(android.os.Looper.getMainLooper())
    }

    private var pendingFinalOrderSave: Runnable? = null

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        log("initZygote, modulePath=${startupParam.modulePath}")
    }
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        log(
            "handleLoadPackage: package=${lpparam.packageName}, process=${lpparam.processName}"
        )

        if (lpparam.packageName != TARGET_PACKAGE){
            return
        }

        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val baseContext  = param.args[0] as Context
                    val classLoader = baseContext .classLoader

                    launcherContext = baseContext.createDeviceProtectedStorageContext()
                    val savedContext = launcherContext

                    log("Application.attach: " +
                            "package=${baseContext .packageName}, " +
                            "classLoader=$classLoader")
                    log(
                        "Launcher context initialized: " +
                                "contextClass=${savedContext?.javaClass?.name}, " +
                                "isDeviceProtected=${savedContext?.isDeviceProtectedStorage}, " +
                                "dataDir=${savedContext?.applicationInfo?.deviceProtectedDataDir}"
                    )

                    savedContext?.let { registerLauncherRestartReceiver(it) }
                    hookLauncher(classLoader)
                }
            }
        )
    }


    // Hook桌面主函数
    private fun hookLauncher(classLoader: ClassLoader) {
        log("hookLauncher called")

        hasHookedLauncher = true

        initConfigReader()


//        hookOnBindCategory(classLoader)
        hookItemInfoCategory(classLoader)
        hookGetCategoryTitle(classLoader)
//        hookNotifyCategoryPageUpdate(classLoader)
        hookCategoryBuildByNativeOrder(classLoader)
//        hookUpdateAdapterItemsTrace(classLoader)
//        hookCategoryTypeCompat(classLoader)

//        checkCategoryTypeClass(classLoader)
//        hookCategoryAdapterItemTrace(classLoader)
    }

    private fun registerLauncherRestartReceiver(context: Context) {
        if (restartReceiverRegistered) {
            return
        }

        restartReceiverRegistered = registerLauncherRestartBroadcastReceiver(
            context = context,
            mainHandler = mainHandler,
            log = ::log
        )
    }

    private fun initConfigReader() {
        configProvider.init()
    }

    private fun getConfig(): DrawerConfig = configProvider.getConfig()

    //
    private fun hookCategoryBuildByNativeOrder(classLoader: ClassLoader) {
        runCatching {
            val appsListClass = XposedHelpers.findClass(
                "com.android.launcher3.allapps.AlphabeticalAppsList",
                classLoader
            )

            /*
             * updateAdapterItems 构造分类列表前，
             * 临时按照 ColorOS 原生保存的 categoryFolderOrder 排列 mApps。
             */
            XposedBridge.hookAllMethods(
                appsListClass,
                "updateAdapterItems",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        val isCategoryPage = runCatching {
                            XposedHelpers.getBooleanField(
                                param.thisObject,
                                "mIsCategoryPage"
                            )
                        }.getOrDefault(false)

                        if (!isCategoryPage) {
                            return
                        }

                        @Suppress("UNCHECKED_CAST")
                        val apps = runCatching {
                            XposedHelpers.getObjectField(
                                param.thisObject,
                                "mApps"
                            ) as? MutableList<Any>
                        }.getOrNull() ?: return

                        /*
                         * 保存原始顺序。
                         * updateAdapterItems 完成后恢复，避免影响字母页。
                         */
                        val originalApps = ArrayList(apps)

                        param.setObjectExtra(
                            EXTRA_ORIGINAL_APPS,
                            originalApps
                        )

                        val config = getConfig()
                        val orderMap = getCurrentNativeOrderMap()

                        sortAppsForCategoryBuild(
                            apps = apps,
                            orderMap = orderMap,
                            config = config
                        )


                        val categorySequence = apps
                            .mapNotNull {
                                getEffectiveCategoryAlias(
                                    appInfo = it,
                                    config = config
                                )
                            }
                            .distinct()
                        log(
                            "category build sequence=$categorySequence, " +
                                    "orderMap=$orderMap"
                        )

                    }

                    override fun afterHookedMethod(
                        param: MethodHookParam
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        val originalApps = param.getObjectExtra(
                            EXTRA_ORIGINAL_APPS
                        ) as? List<Any> ?: return

                        @Suppress("UNCHECKED_CAST")
                        val apps = runCatching {
                            XposedHelpers.getObjectField(
                                param.thisObject,
                                "mApps"
                            ) as? MutableList<Any>
                        }.getOrNull() ?: return

                        @Suppress("UNCHECKED_CAST")
                        val categoryItems = runCatching {
                            XposedHelpers.getObjectField(
                                param.thisObject,
                                "mCategoryAdapterItems"
                            ) as? List<Any>
                        }.getOrNull()

                        if (!categoryItems.isNullOrEmpty()) {
                            updateNativeCategorySnapshotFromItems(categoryItems)
                        }

                        /*
                         * 分类列表已经创建完毕。
                         * 恢复 mApps，防止破坏其他页面的应用顺序。
                         */
                        apps.clear()
                        apps.addAll(originalApps)
                    }
                }
            )

            /*
             * ColorOS 拖拽过程中会调用 saveItemsOrder。
             * 这里只捕获最新顺序，不修改、不保存任何内容。
             */
            val appCategoryTypeClass = XposedHelpers.findClass(
                "com.android.launcher3.allapps.appcategory.AppCategoryType",
                classLoader
            )

            XposedBridge.hookAllMethods(
                appCategoryTypeClass,
                "saveItemsOrder",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(
                        param: MethodHookParam
                    ) {
                        val items =
                            param.args.getOrNull(0) as? List<*>
                                ?: return

                        val captured =
                            extractNativeOrderFromItems(items)

                        if (captured.isNotEmpty()) {
                            latestNativeOrderMap = captured

                            log(
                                "captured native drag order=$captured"
                            )
                        }
                    }
                }
            )

            XposedBridge.hookAllMethods(
                appCategoryTypeClass,
                "restoreCategoryOrder",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(
                        param: MethodHookParam
                    ) {
                        latestNativeOrderMap = null
                    }
                }
            )

            log("hook category build by native order success")
        }.onFailure {
            log(
                "hook category build by native order failed: " +
                        "${it.javaClass.name}: ${it.message}"
            )
            logThrowable(it)
        }
    }
    private fun sortAppsForCategoryBuild(apps: MutableList<Any>, orderMap: Map<String, Int>, config: DrawerConfig) {
        if (apps.size < 2) {
            return
        }

        val normalOrders = orderMap.values
            .filter { it in 0 until 200 }

        val maxNormalOrder =
            normalOrders.maxOrNull() ?: 0

        /*
         * List.sortWith 在 Android/Java 中是稳定排序。
         * 同一分类的应用比较结果为 0，
         * 因而保持原始相对顺序。
         */
        apps.sortWith { first, second ->
            val firstAlias = getEffectiveCategoryAlias(
                appInfo = first,
                config = config
            )

            val secondAlias = getEffectiveCategoryAlias(
                appInfo = second,
                config = config
            )

            val firstOrder = resolveCategoryOrder(
                alias = firstAlias,
                orderMap = orderMap,
                config = config,
                maxNormalOrder = maxNormalOrder
            )

            val secondOrder = resolveCategoryOrder(
                alias = secondAlias,
                orderMap = orderMap,
                config = config,
                maxNormalOrder = maxNormalOrder
            )

            firstOrder.compareTo(secondOrder)
        }
    }
    private fun getEffectiveCategoryAlias(appInfo: Any, config: DrawerConfig): String? {
        val packageName =
            getPackageNameFromAppInfo(appInfo)

        /*
         * 模块配置优先。
         * 这样不依赖是否已经调用过 Hook 后的方法。
         */
        if (packageName != null) {
            val customAlias =
                config.packageToCategory[packageName]

            if (customAlias != null) {
                return customAlias
            }
        }

        /*
         * 普通 App 读取 ColorOS 原有类别。
         * 该调用也会经过现有的 getRealEnumCategoryType Hook。
         */
        return runCatching {
            XposedHelpers.callMethod(
                appInfo,
                "getRealEnumCategoryType"
            ) as? String
        }.getOrNull()
    }
    private fun resolveCategoryOrder(alias: String?, orderMap: Map<String, Int>, config: DrawerConfig, maxNormalOrder: Int): Int {
        if (alias == null) {
            return Int.MAX_VALUE
        }

        val savedOrder = orderMap[alias]

        /*
         * 0～199 是正常显示顺序。
         */
        if (savedOrder != null && savedOrder < 200) {
            return savedOrder
        }

        /*
         * 自定义分类第一次出现、还没有拖拽记录时，
         * 稳定地放在现有分类末尾。
         */
        val customIndex = config.categories
            .map { it.alias }
            .distinct()
            .indexOf(alias)

        if (customIndex >= 0) {
            return maxNormalOrder + 1 + customIndex
        }

        /*
         * ColorOS 中 200 通常表示未显示或兜底分类。
         */
        return savedOrder ?: 200
    }
    private fun getCurrentNativeOrderMap(): Map<String, Int> {
        /*
         * 拖拽后的内存顺序优先，
         * 避免 SharedPreferences.apply() 尚未落盘。
         */
        latestNativeOrderMap?.let {
            return it
        }

        val context = launcherContext ?: run {
            log("getCurrentNativeOrderMap: launcherContext=null")
            return emptyMap()
        }

        val prefs = context.getSharedPreferences(
            NATIVE_ORDER_PREF_NAME,
            Context.MODE_PRIVATE
        )

        val json = prefs.getString(
            NATIVE_ORDER_KEY,
            null
        )

        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        val result = linkedMapOf<String, Int>()

        runCatching {
            val jsonObject = JSONObject(json)
            val keys = jsonObject.keys()

            while (keys.hasNext()) {
                val alias = keys.next()
                result[alias] =
                    jsonObject.getInt(alias)
            }
        }.onFailure {
            log(
                "read native order failed: " +
                        "${it.javaClass.name}: ${it.message}"
            )
        }

        return result
    }
    private fun extractNativeOrderFromItems(items: List<*>): Map<String, Int> {
        val result = linkedMapOf<String, Int>()

        for (item in items) {
            if (item == null) continue

            val viewType = runCatching {
                XposedHelpers.getIntField(
                    item,
                    "viewType"
                )
            }.getOrNull() ?: continue

            if (viewType != 1) continue

            val alias = runCatching {
                XposedHelpers.getObjectField(
                    item,
                    "enumType"
                ) as? String
            }.getOrNull() ?: continue

            val order = runCatching {
                XposedHelpers.getIntField(
                    item,
                    "categoryFolderOrder"
                )
            }.getOrNull() ?: continue

            result[alias] = order
        }

        return result
    }

    //
    private fun isCustomCategoryAlias(alias: String): Boolean {
        return alias !in officialCategoryAliases
    }
    private fun hookCategoryAdapterItemTrace(classLoader: ClassLoader) {
        runCatching {
            val clazz = XposedHelpers.findClass(
                "com.android.launcher3.allapps.BaseAllAppsAdapter\$CategoryAdapterItem",
                classLoader
            )

            XposedBridge.hookAllConstructors(
                clazz,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val enumType = runCatching {
                            XposedHelpers.getObjectField(
                                param.thisObject,
                                "enumType"
                            ) as? String
                        }.getOrNull() ?: return

                        if (!isCustomCategoryAlias(enumType)) {
                            return
                        }

                        val order = runCatching {
                            XposedHelpers.getIntField(
                                param.thisObject,
                                "categoryFolderOrder"
                            )
                        }.getOrNull()

                        log(
                            "CategoryAdapterItem created: " +
                                    "enumType=$enumType, order=$order"
                        )
                    }
                }
            )

            log("hook CategoryAdapterItem trace success")
        }.onFailure {
            log(
                "hook CategoryAdapterItem trace failed: " +
                        "${it.javaClass.name}: ${it.message}"
            )
        }
    }

    // 控制应用属于哪个分类。
    private fun hookItemInfoCategory(classLoader: ClassLoader) {
        runCatching {
            val clazz = XposedHelpers.findClass(
                "com.android.launcher3.model.data.ItemInfo",
                classLoader
            )

            XposedBridge.hookAllMethods(
                clazz,
                "getRealEnumCategoryType",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val itemInfo = param.thisObject
                        val pkg = getPackageNameFromAppInfo(itemInfo) ?: return
                        val oldType = param.result as? String

                        val config = getConfig()
                        val newType = config.packageToCategory[pkg]
//                        if (
//                            pkg == "com.openai.chatgpt" ||
//                            pkg == "com.aliyun.tongyi" ||
//                            pkg == "com.larus.nova" ||
//                            pkg == "com.google.android.apps.bard" ||
//                            pkg == "bin.mt.plus" ||
//                            pkg == "moe.shizuku.privileged.api" ||
//                            pkg == "li.songe.gkd"
//                        ) {
//                            log(
//                                "debug mapping: pkg=$pkg, oldType=$oldType, " +
//                                        "newType=$newType, categories=${config.categories.size}"
//                            )
//                        }
                        if (newType != null && newType != oldType) {
                            param.result = newType
//                            log("change category: pkg=$pkg, $oldType -> $newType")
                        } else if (
                            newType == null &&
                            shouldPutNewAppToOther(pkg) &&
                            oldType != "other"
                        ) {
                            param.result = "other"
                            log("new app default to other: pkg=$pkg, oldType=$oldType")
                        }

                    }
                }
            )

            log("hook ItemInfo.getRealEnumCategoryType success")
        }.onFailure {
            log("hook ItemInfo.getRealEnumCategoryType failed: ${it.javaClass.name}: ${it.message}")
            logThrowable(it)
        }
    }

    private fun shouldPutNewAppToOther(packageName: String): Boolean =
        configProvider.shouldPutNewAppToOther(packageName)

    private fun updateNativeCategorySnapshotFromItems(categoryItems: List<Any>) {
        val snapshot = linkedMapOf<String, MutableSet<String>>()
        val titleSnapshot = linkedMapOf<String, MutableMap<String, String>>()

        for (categoryItem in categoryItems) {
            val alias = getCategoryEnumType(categoryItem)
                ?: continue

            @Suppress("UNCHECKED_CAST")
            val itemInfoList = runCatching {
                XposedHelpers.getObjectField(
                    categoryItem,
                    "itemInfoList"
                ) as? List<Any>
            }.getOrNull() ?: continue

            val packages = snapshot.getOrPut(alias) {
                linkedSetOf()
            }
            val titles = titleSnapshot.getOrPut(alias) {
                linkedMapOf()
            }

            itemInfoList.forEach { appInfo ->
                getPackageNameFromAppInfo(appInfo)?.let { packageName ->
                    packages.add(packageName)

                    val title = getTitleFromAppInfo(appInfo)
                    if (!title.isNullOrBlank()) {
                        titles[packageName] = title
                    }
                }
            }
        }

        if (snapshot.isEmpty()) {
            return
        }

        nativeCategoryPackages.clear()
        nativeCategoryPackages.putAll(snapshot)
        nativeCategoryAppTitles.clear()
        nativeCategoryAppTitles.putAll(titleSnapshot)

        pendingSystemSnapshotBroadcast?.let {
            mainHandler.removeCallbacks(it)
        }

        val task = Runnable {
            pendingSystemSnapshotBroadcast = null
            broadcastSystemCategorySnapshot()
        }

        pendingSystemSnapshotBroadcast = task
        mainHandler.postDelayed(task, 500L)
    }

    private fun broadcastSystemCategorySnapshot() {
        val context = launcherContext ?: return

        val root = JSONObject()
        val categories = JSONArray()
        val packagesInSpecificCategories = nativeCategoryPackages
            .filterKeys { it != "other" && it != "suggestion" }
            .values
            .flatten()
            .toSet()

        nativeCategoryPackages.forEach { (alias, packages) ->
            val packagesJson = JSONArray()
            val appsJson = JSONArray()
            val normalizedPackages = if (alias == "other") {
                packages.filter { it !in packagesInSpecificCategories }
            } else {
                packages.toList()
            }

            normalizedPackages.sorted().forEach { packageName ->
                packagesJson.put(packageName)

                appsJson.put(
                    JSONObject()
                        .put("package", packageName)
                        .put(
                            "title",
                            nativeCategoryAppTitles[alias]?.get(packageName).orEmpty()
                        )
                )
            }

            categories.put(
                JSONObject()
                    .put("alias", alias)
                    .put("packages", packagesJson)
                    .put("apps", appsJson)
            )
        }

        val json = root
            .put("categories", categories)
            .toString()

        if (json == lastSystemSnapshotJson) {
            return
        }

        lastSystemSnapshotJson = json

        runCatching {
            context.sendBroadcast(
                Intent(ConfigStore.ACTION_SYSTEM_CATEGORY_SNAPSHOT)
                    .setPackage(ConfigStore.MODULE_PACKAGE)
                    .putExtra(
                        ConfigStore.EXTRA_SYSTEM_CATEGORY_JSON,
                        json
                    )
            )
            log("system category snapshot broadcasted, categories=${nativeCategoryPackages.size}")
        }.onFailure {
            log(
                "system category snapshot broadcast failed: " +
                        "${it.javaClass.name}: ${it.message}"
            )
        }
    }


    // 控制自定义分类显示标题
    private fun hookGetCategoryTitle(classLoader: ClassLoader) {
        runCatching {
            val clazz = XposedHelpers.findClass(
                CATEGORY_TITLE_CLASS,
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                clazz,
                "getCategoryTitle",
                Context::class.java,
                String::class.java,
                object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        val categoryType = param.args[1] as? String
//                        log("getCategoryTitle before: categoryType=$categoryType")
//                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val categoryType = param.args.getOrNull(1) as? String ?: return

                        val config = getConfig()
                        val customTitle = config.categoryTitles[categoryType]

                        if (
                            customTitle != null &&
                            categoryType !in officialCategoryAliases
                        ) {
                            param.result = customTitle
                            log("custom title: $categoryType -> $customTitle")
                        }
                    }
                }
            )

            log("hook getCategoryTitle success")
        }.onFailure {
            log("hook getCategoryTitle failed: ${it.javaClass.name}: ${it.message}")
            logThrowable(it)
        }
    }


    // 控制分类顺序，以及分类内应用顺序
    private fun hookCategoryOrderAndAppOrder(classLoader: ClassLoader) {
        runCatching {
            val clazz = XposedHelpers.findClass(
                "com.android.launcher3.allapps.AlphabeticalAppsList",
                classLoader
            )

            XposedBridge.hookAllMethods(
                clazz,
                "updateAdapterItems",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val isCategoryPage = runCatching {
                            XposedHelpers.getBooleanField(param.thisObject, "mIsCategoryPage")
                        }.getOrDefault(false)

                        if (!isCategoryPage) return

                        @Suppress("UNCHECKED_CAST")
                        val categoryItems = runCatching {
                            XposedHelpers.getObjectField(param.thisObject, "mCategoryAdapterItems")
                                    as? MutableList<Any>
                        }.getOrNull() ?: return

                        val config = getConfig()

                        log("category order and app order follows ColorOS native behavior")
                    }
                }
            )

            log("hook category order/app order success")
        }.onFailure {
            log("hook category order/app order failed: ${it.javaClass.name}: ${it.message}")
            logThrowable(it)
        }
    }
    private fun hookNotifyCategoryPageUpdate(classLoader: ClassLoader) {
        runCatching {
            val clazz = XposedHelpers.findClass(
                "com.android.launcher3.allapps.AlphabeticalAppsList",
                classLoader
            )

            XposedBridge.hookAllMethods(
                clazz,
                "notifyCategoryPageUpdate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = getConfig()

                        @Suppress("UNCHECKED_CAST")
                        val newItems = param.args.getOrNull(1) as? MutableList<Any>
                            ?: return

                        log("notifyCategoryPageUpdate before: native order newItems=${newItems.size}")
                    }
                }
            )

            log("hook notifyCategoryPageUpdate success")
        }.onFailure {
            log("hook notifyCategoryPageUpdate failed: ${it.javaClass.name}: ${it.message}")
            logThrowable(it)
        }
    }
    private fun hookUpdateAdapterItemsBeforeSort(classLoader: ClassLoader) {
        val classNames = listOf(
            "com.android.launcher3.allapps.AlphabeticalAppsList",
            "com.android.launcher3.allapps.OplusAlphabeticalAppsList"
        )

        for (className in classNames) {
            runCatching {
                val clazz = XposedHelpers.findClass(className, classLoader)

                XposedBridge.hookAllMethods(
                    clazz,
                    "updateAdapterItems",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val isCategoryPage = runCatching {
                                XposedHelpers.getBooleanField(param.thisObject, "mIsCategoryPage")
                            }.getOrDefault(false)

                            if (!isCategoryPage) return

                            @Suppress("UNCHECKED_CAST")
                            val apps = runCatching {
                                XposedHelpers.getObjectField(param.thisObject, "mApps") as? MutableList<Any>
                            }.getOrNull() ?: return

                            val config = getConfig()
                            sortAppsBeforeCategoryBuild(apps, config)

                            log("sort mApps before updateAdapterItems: class=$className, appCount=${apps.size}")
                        }
                    }
                )

                log("hook updateAdapterItems before-sort success: $className")
            }.onFailure {
                log("hook updateAdapterItems before-sort failed: $className, ${it.javaClass.name}: ${it.message}")
            }
        }
    }
    private fun sortAppsBeforeCategoryBuild(apps: MutableList<Any>, config: DrawerConfig) {
        return
        val categoryOrder = emptyList<String>()

        apps.sortWith { a, b ->
            val pkgA = getPackageNameFromAppInfo(a)
            val pkgB = getPackageNameFromAppInfo(b)

            val typeA = getEffectiveCategoryType(a, pkgA, config)
            val typeB = getEffectiveCategoryType(b, pkgB, config)

            val categoryIndexA = getOrderIndex(categoryOrder, typeA)
            val categoryIndexB = getOrderIndex(categoryOrder, typeB)

            if (categoryIndexA != categoryIndexB) {
                return@sortWith categoryIndexA.compareTo(categoryIndexB)
            }

            val appIndexA = getAppIndexInCategory(pkgA, typeA, config)
            val appIndexB = getAppIndexInCategory(pkgB, typeB, config)

            if (appIndexA != appIndexB) {
                return@sortWith appIndexA.compareTo(appIndexB)
            }

            // 没有在配置中指定顺序的应用，保持一个稳定兜底顺序
            val titleA = getTitleFromAppInfo(a) ?: ""
            val titleB = getTitleFromAppInfo(b) ?: ""

            titleA.compareTo(titleB)
        }
    }
    private fun getEffectiveCategoryType(appInfo: Any, pkg: String?, config: DrawerConfig): String? {
        // 配置里指定的分类优先
        val customType = pkg?.let { config.packageToCategory[it] }
        if (customType != null) return customType

        // 否则读取系统原始分类
        return runCatching {
            val method = appInfo.javaClass.methods.firstOrNull {
                it.name == "getRealEnumCategoryType" && it.parameterCount == 0
            }

            method?.invoke(appInfo) as? String
        }.getOrNull()
    }

    private fun getOrderIndex(order: List<String>, type: String?): Int {
        if (type == null) return Int.MAX_VALUE

        val index = order.indexOf(type)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun getAppIndexInCategory(pkg: String?, type: String?, config: DrawerConfig): Int {
        if (pkg == null || type == null) return Int.MAX_VALUE

        val desiredPackages = config.categoryPackages[type] ?: return Int.MAX_VALUE

        val index = desiredPackages.indexOf(pkg)
        return if (index >= 0) index else Int.MAX_VALUE
    }
    private fun sortCategoryItems(categoryItems: MutableList<Any>, config: DrawerConfig) {
        val order = emptyList<String>()

        categoryItems.sortWith { a, b ->
            val typeA = getCategoryEnumType(a)
            val typeB = getCategoryEnumType(b)

            val indexA = order.indexOf(typeA).let { if (it >= 0) it else Int.MAX_VALUE }
            val indexB = order.indexOf(typeB).let { if (it >= 0) it else Int.MAX_VALUE }

            indexA.compareTo(indexB)
        }
    }
    private fun getCategoryEnumType(item: Any): String? {
        return runCatching {
            XposedHelpers.getObjectField(item, "enumType") as? String
        }.getOrNull()
    }
    private fun sortAppsInsideCategories(categoryItems: MutableList<Any>, config: DrawerConfig) {
        for (categoryItem in categoryItems) {
            val enumType = getCategoryEnumType(categoryItem) ?: continue
            val desiredPackages = config.categoryPackages[enumType] ?: continue

            @Suppress("UNCHECKED_CAST")
            val itemInfoList = runCatching {
                XposedHelpers.getObjectField(categoryItem, "itemInfoList") as? MutableList<Any>
            }.getOrNull() ?: continue

            itemInfoList.sortWith { a, b ->
                val pkgA = getPackageNameFromAppInfo(a)
                val pkgB = getPackageNameFromAppInfo(b)

                val indexA = desiredPackages.indexOf(pkgA).let { if (it >= 0) it else Int.MAX_VALUE }
                val indexB = desiredPackages.indexOf(pkgB).let { if (it >= 0) it else Int.MAX_VALUE }

                indexA.compareTo(indexB)
            }

            log("sort apps in category=$enumType, count=${itemInfoList.size}")
        }
    }


    // Hook类别绑定
    private fun hookOnBindCategory(classLoader: ClassLoader) {
        runCatching {
            val clazz = XposedHelpers.findClass(
                "com.android.launcher3.allapps.OplusCategoryIconContainer",
                classLoader
            )

            XposedBridge.hookAllMethods(
                clazz,
                "onBindCategory",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val categoryAdapterItem = param.args.getOrNull(0)

                        log("onBindCategory called, itemClass=${categoryAdapterItem?.javaClass?.name}")
                        if (categoryAdapterItem != null) {
                            dumpCategoryAdapterItem(categoryAdapterItem)
                        }
                    }
                }
            )

            log("hook onBindCategory success")
        }.onFailure {
            log("hook onBindCategory failed: ${it.javaClass.name}: ${it.message}")
            logThrowable(it)
        }
    }
    private fun dumpCategoryAdapterItem(item: Any) {
        runCatching {
            val enumType = XposedHelpers.getObjectField(item, "enumType") as? String

            @Suppress("UNCHECKED_CAST")
            val itemInfoList = XposedHelpers.getObjectField(item, "itemInfoList") as? List<Any>

            log("CategoryAdapterItem enumType=$enumType, appCount=${itemInfoList?.size}")

            itemInfoList?.take(60)?.forEachIndexed { index, appInfo ->
                val pkg = getPackageNameFromAppInfo(appInfo)
                val title = getTitleFromAppInfo(appInfo)

                log("  [$index] pkg=$pkg, title=$title, class=${appInfo.javaClass.name}")
            }
        }.onFailure {
            log("dumpCategoryAdapterItem failed: ${it.javaClass.name}: ${it.message}")
            logThrowable(it)
        }
    }
    private fun getTitleFromAppInfo(appInfo: Any): String? {
        return runCatching {
            val title = XposedHelpers.getObjectField(appInfo, "title")
            title?.toString()
        }.getOrNull()
    }
    private fun getPackageNameFromAppInfo(appInfo: Any): String? {
        // 优先从 componentName 取
        runCatching {
            val componentName = XposedHelpers.getObjectField(appInfo, "componentName")
            if (componentName is android.content.ComponentName) {
                return componentName.packageName
            }
        }

        return null
    }



    private fun hookUpdateAdapterItemsTrace(classLoader: ClassLoader) {
        runCatching {
            val clazz = XposedHelpers.findClass(
                "com.android.launcher3.allapps.AlphabeticalAppsList",
                classLoader
            )

            XposedBridge.hookAllMethods(
                clazz,
                "updateAdapterItems",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val isCategoryPage = runCatching {
                            XposedHelpers.getBooleanField(param.thisObject, "mIsCategoryPage")
                        }.getOrDefault(false)

                        log("updateAdapterItems before: args=${param.args.contentToString()}, isCategoryPage=$isCategoryPage")
                    }
                }
            )

            log("hook updateAdapterItems trace success")
        }.onFailure {
            log("hook updateAdapterItems trace failed: ${it.javaClass.name}: ${it.message}")
            logThrowable(it)
        }
    }

    private fun isDebugLogEnabled(): Boolean = configProvider.isDebugLogEnabled()

    private fun log(message: String) {
        if (isDebugLogEnabled()) {
            XposedBridge.log("[ColorOSDrawerHook] $message")
        }
    }

    private fun logThrowable(throwable: Throwable) {
        if (isDebugLogEnabled()) {
            XposedBridge.log(throwable)
        }
    }
}


