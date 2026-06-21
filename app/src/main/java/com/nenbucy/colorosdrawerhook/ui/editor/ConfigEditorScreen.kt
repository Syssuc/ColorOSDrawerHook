package com.nenbucy.colorosdrawerhook.ui.editor

import com.nenbucy.colorosdrawerhook.DrawerConfig
import com.nenbucy.colorosdrawerhook.model.CATEGORY_LABEL_SEPARATOR
import com.nenbucy.colorosdrawerhook.model.EditableCategory
import com.nenbucy.colorosdrawerhook.model.InstalledApp
import com.nenbucy.colorosdrawerhook.model.assignPackageToCategory
import com.nenbucy.colorosdrawerhook.model.effectiveAppCount
import com.nenbucy.colorosdrawerhook.model.effectivePackages
import com.nenbucy.colorosdrawerhook.model.explicitPackageToCategory
import com.nenbucy.colorosdrawerhook.model.generateUniqueCategoryAlias
import com.nenbucy.colorosdrawerhook.model.toDrawerConfig
import com.nenbucy.colorosdrawerhook.model.toEditableCategories
import com.nenbucy.colorosdrawerhook.model.toPrettyJson
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigEditorScreen(
    initialConfig: DrawerConfig,
    initialSystemCategoryPackages: Map<String, List<String>>,
    initialSystemAppTitles: Map<String, String>,
    initialAutoNewAppsToOther: Boolean,
    initialHideEmptySystemCategories: Boolean,
    initialDebugLogEnabled: Boolean,
    initialBaselinePackages: Set<String>,
    loadInstalledApps: () -> List<InstalledApp>,
    loadAppsForPackages: (Set<String>, Map<String, String>) -> List<InstalledApp>,
    onAutoNewAppsToOtherChange: (Boolean) -> Boolean,
    onHideEmptySystemCategoriesChange: (Boolean) -> Boolean,
    onDebugLogEnabledChange: (Boolean) -> Boolean,
    onSave: (DrawerConfig) -> Boolean,
    onRestartLauncher: () -> Boolean,
    onReset: () -> DrawerConfig
) {
    val categories = remember {
        mutableStateListOf<EditableCategory>().apply {
            addAll(initialConfig.toEditableCategories(initialSystemCategoryPackages))
        }
    }
    var savedPackageToCategory by remember { mutableStateOf(initialConfig.packageToCategory) }
    var detailIndex by remember { mutableIntStateOf(-1) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var manualPackage by remember { mutableStateOf("") }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var autoNewAppsToOther by remember { mutableStateOf(initialAutoNewAppsToOther) }
    var hideEmptySystemCategories by remember { mutableStateOf(initialHideEmptySystemCategories) }
    var debugLogEnabled by remember { mutableStateOf(initialDebugLogEnabled) }
    var baselinePackages by remember { mutableStateOf(initialBaselinePackages) }
    var saveEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var pendingBackupJson by remember { mutableStateOf("") }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer -> writer.write(pendingBackupJson) }
                ?: error("open output stream failed")
        }.onSuccess {
            Toast.makeText(context, "配置已备份", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "备份失败", Toast.LENGTH_SHORT).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                ?.use { reader -> reader.readText() }
                ?: error("open input stream failed")
            DrawerConfig.parse(json).also { restoredConfig ->
                require(restoredConfig.categories.isNotEmpty())
            }
        }.onSuccess { restoredConfig ->
            categories.clear()
            categories.addAll(restoredConfig.toEditableCategories(initialSystemCategoryPackages))
            savedPackageToCategory = restoredConfig.packageToCategory
            detailIndex = -1
            searchText = ""
            manualPackage = ""
            Toast.makeText(
                context,
                "已恢复备份，保存并重启后生效",
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure {
            Toast.makeText(context, "恢复失败，请检查备份文件", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps()
    }

    DisposableEffect(context, loadInstalledApps) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                installedApps = loadInstalledApps()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        context.registerReceiver(receiver, filter)

        onDispose {
            runCatching {
                context.unregisterReceiver(receiver)
            }
        }
    }

    val selectedCategory = categories.getOrNull(detailIndex)
    val config = categories.toDrawerConfig()
    val explicitPackageToCategory = categories.explicitPackageToCategory()
    val autoOtherPackages = remember(
        installedApps,
        autoNewAppsToOther,
        baselinePackages,
        explicitPackageToCategory
    ) {
        if (!autoNewAppsToOther) {
            emptySet()
        } else {
            installedApps
                .map { it.packageName }
                .filter { packageName ->
                    packageName !in baselinePackages &&
                            packageName !in explicitPackageToCategory
                }
                .toSet()
        }
    }
    val referencedPackageNames = remember(
        categories.toList(),
        autoOtherPackages
    ) {
        categories
            .flatMap { it.packages + it.nativePackages }
            .plus(autoOtherPackages)
            .toSet()
    }
    val displayApps = remember(
        installedApps,
        referencedPackageNames
    ) {
        val installedPackageNames = installedApps.map { it.packageName }.toSet()
        val missingPackages = referencedPackageNames - installedPackageNames

        (installedApps + loadAppsForPackages(missingPackages, initialSystemAppTitles))
            .distinctBy { it.componentName }
            .sortedWith(
                compareBy<InstalledApp> { it.label.lowercase() }
                    .thenBy { it.packageName }
            )
    }
    val installedPackageNames = remember(displayApps) {
        displayApps.map { it.packageName }.toSet()
    }
    val visibleCategoryCount = categories.count { category ->
        !hideEmptySystemCategories ||
                category.effectiveAppCount(
                    explicitPackageToCategory,
                    autoOtherPackages,
                    installedPackageNames,
                    displayApps
                ) > 0
    }
    val packageCategoryLabels = remember(
        categories.toList(),
        explicitPackageToCategory,
        autoOtherPackages,
        installedPackageNames,
        displayApps
    ) {
        val result = linkedMapOf<String, MutableList<String>>()

        categories.forEach { category ->
            category.effectivePackages(
                explicitPackageToCategory,
                autoOtherPackages,
                installedPackageNames
            ).forEach { packageName ->
                result.getOrPut(packageName) { mutableListOf() }
                    .add(category.title)
            }
        }

        result.mapValues { (_, titles) ->
            titles.distinct().joinToString(CATEGORY_LABEL_SEPARATOR)
        }
    }
BackHandler(enabled = selectedCategory != null) {
        detailIndex = -1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selectedCategory != null) {
                        IconButton(onClick = { detailIndex = -1 }) {
                            Text(
                                text = "‹",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(selectedCategory?.title ?: "ColorOS 分类配置")
                        Text(
                            text = if (selectedCategory == null) {
                                "${visibleCategoryCount} 个分类，${categories.sumOf { it.effectiveAppCount(explicitPackageToCategory, autoOtherPackages, installedPackageNames, displayApps) }} 个应用"
                            } else {
                                "${selectedCategory.effectiveAppCount(explicitPackageToCategory, autoOtherPackages, installedPackageNames, displayApps)} 个应用"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Text(
                                text = "☰",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("新应用进其他") },
                                leadingIcon = {
                                    Checkbox(
                                        checked = autoNewAppsToOther,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    val newValue = !autoNewAppsToOther
                                    if (onAutoNewAppsToOtherChange(newValue)) {
                                        autoNewAppsToOther = newValue
                                        if (newValue && baselinePackages.isEmpty()) {
                                            baselinePackages = installedApps
                                                .map { it.packageName }
                                                .toSet()
                                        }
                                        Toast.makeText(
                                            context,
                                            if (newValue) {
                                                "已开启，当前应用已记录为基准"
                                            } else {
                                                "已关闭"
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                                                } else {
                                        Toast.makeText(
                                            context,
                                            "设置保存失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                                                }
                                    showOptionsMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("隐藏空类别") },
                                leadingIcon = {
                                    Checkbox(
                                        checked = hideEmptySystemCategories,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    val newValue = !hideEmptySystemCategories
                                    if (onHideEmptySystemCategoriesChange(newValue)) {
                                        hideEmptySystemCategories = newValue
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "设置保存失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                                                }
                                    showOptionsMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Debug") },
                                leadingIcon = {
                                    Checkbox(
                                        checked = debugLogEnabled,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    val newValue = !debugLogEnabled
                                    if (onDebugLogEnabledChange(newValue)) {
                                        debugLogEnabled = newValue
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "设置保存失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                                                }
                                    showOptionsMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("备份配置") },
                                onClick = {
                                    showOptionsMenu = false
                                    pendingBackupJson = config.toPrettyJson()
                                    backupLauncher.launch("coloros_drawer_config.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("恢复配置") },
                                onClick = {
                                    showOptionsMenu = false
                                    restoreLauncher.launch(
                                        arrayOf("application/json", "text/*", "*/*")
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("查看 JSON") },
                                onClick = {
                                    showOptionsMenu = false
                                    showJsonDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("恢复默认") },
                                onClick = {
                                    showOptionsMenu = false
                                    val resetConfig = onReset()
                                    categories.clear()
                                    categories.addAll(
                                        resetConfig.toEditableCategories(initialSystemCategoryPackages)
                                    )
                                    savedPackageToCategory = resetConfig.packageToCategory
                                    detailIndex = -1
                                    searchText = ""
                                    manualPackage = ""
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (saveEnabled) {
                        showSaveConfirmDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            if (selectedCategory == null) {
                CategoryListScreen(
                    categories = categories,
                    explicitPackageToCategory = explicitPackageToCategory,
                    autoOtherPackages = autoOtherPackages,
                    installedPackageNames = installedPackageNames,
                    displayApps = displayApps,
                    hideEmptySystemCategories = hideEmptySystemCategories,
                    onOpen = {
                        searchText = ""
                        detailIndex = it
                    },
                    onAdd = {
                        editingIndex = null
                        showCategoryDialog = true
                    },
                    onEdit = { index ->
                        editingIndex = index
                        showCategoryDialog = true
                    },
                    onDelete = { index ->
                        categories.removeAt(index)
                    }
                )
            } else {
                CategoryEditor(
                    category = selectedCategory,
                    explicitPackageToCategory = explicitPackageToCategory,
                    autoOtherPackages = autoOtherPackages,
                    installedPackageNames = installedPackageNames,
                    installedApps = displayApps,
                    currentCategoryLabels = packageCategoryLabels,
                    categoryFilterOptions = categories.map { it.title }.distinct(),
                    savedPackageToCategory = savedPackageToCategory,
                    searchText = searchText,
                    manualPackage = manualPackage,
                    onSearchChange = { searchText = it },
                    onManualPackageChange = { manualPackage = it },
                    onEditCategory = {
                        editingIndex = detailIndex
                        showCategoryDialog = true
                    },
                    onDeleteCategory = {
                        categories.removeAt(detailIndex)
                        detailIndex = -1
                    },
                    onTogglePackage = { packageName, checked ->
                        if (checked) {
                            categories.assignPackageToCategory(detailIndex, packageName)
                        } else if (savedPackageToCategory[packageName] != selectedCategory.alias) {
                            categories[detailIndex] = selectedCategory.copy(
                                packages = selectedCategory.packages - packageName
                            )
                        }
                    },
                    onAddManualPackage = {
                        val packageName = manualPackage.trim()
                        if (packageName.isNotBlank()) {
                            categories.assignPackageToCategory(detailIndex, packageName)
                            manualPackage = ""
                        }
                    }
                )
            }
        }
    }

    if (showCategoryDialog) {
        CategoryDialog(
            category = editingIndex?.let { categories.getOrNull(it) },
            onDismiss = { showCategoryDialog = false },
            onConfirm = { alias, title ->
                val normalizedTitle = title.trim()
                val normalizedAlias = alias.trim().ifBlank {
                    generateUniqueCategoryAlias(
                        title = normalizedTitle,
                        categories = categories,
                        editingIndex = editingIndex
                    )
                }
                val conflict = categories.withIndex().any { (index, item) ->
                    item.alias == normalizedAlias && index != editingIndex
                }

                when {
                    normalizedTitle.isBlank() -> {
                        Toast.makeText(context, "分类名称不能为空", Toast.LENGTH_SHORT).show()
                                                }

                    conflict -> {
                        Toast.makeText(context, "分类内部标识已存在", Toast.LENGTH_SHORT).show()
                                                }

                    editingIndex == null -> {
                        val insertIndex = categories.indexOfFirst { it.isSystem }
                            .let { if (it >= 0) it else categories.size }
                        categories.add(
                            insertIndex,
                            EditableCategory(
                                alias = normalizedAlias,
                                title = normalizedTitle,
                                packages = emptyList()
                            )
                        )
                        detailIndex = insertIndex
                        showCategoryDialog = false
                    }

                    else -> {
                        val index = editingIndex ?: return@CategoryDialog
                        val old = categories[index]
                        categories[index] = old.copy(
                            alias = normalizedAlias,
                            title = normalizedTitle
                        )
                        showCategoryDialog = false
                    }
                }
            }
        )
    }

    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = { Text("保存并重启桌面？") },
            text = { Text("保存后将自动重启 ColorOS 桌面，使分类配置立即生效。") },
            confirmButton = {
                Button(
                    enabled = saveEnabled,
                    onClick = {
                        showSaveConfirmDialog = false
                        saveEnabled = false
                        if (onSave(config)) {
                            savedPackageToCategory = config.packageToCategory
                            if (onRestartLauncher()) {
                                Toast.makeText(
                                    context,
                                    "正在重启桌面",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        saveEnabled = true
                    }
                ) {
                    Text("保存并重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showJsonDialog) {
        JsonDialog(
            json = config.toPrettyJson(),
            onDismiss = { showJsonDialog = false }
        )
    }
}
