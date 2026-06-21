package com.nenbucy.colorosdrawerhook.ui.editor

import com.nenbucy.colorosdrawerhook.model.CATEGORY_LABEL_SEPARATOR
import com.nenbucy.colorosdrawerhook.model.EditableCategory
import com.nenbucy.colorosdrawerhook.model.InstalledApp
import com.nenbucy.colorosdrawerhook.model.UNCATEGORIZED_FILTER_LABEL
import com.nenbucy.colorosdrawerhook.model.effectivePackages
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
@Composable
internal fun CategoryEditor(
    category: EditableCategory,
    explicitPackageToCategory: Map<String, String>,
    autoOtherPackages: Set<String>,
    installedPackageNames: Set<String>,
    installedApps: List<InstalledApp>,
    currentCategoryLabels: Map<String, String>,
    categoryFilterOptions: List<String>,
    savedPackageToCategory: Map<String, String>,
    searchText: String,
    manualPackage: String,
    onSearchChange: (String) -> Unit,
    onManualPackageChange: (String) -> Unit,
    onEditCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onTogglePackage: (String, Boolean) -> Unit,
    onAddManualPackage: () -> Unit
) {
    var showManualDialog by remember { mutableStateOf(false) }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember(category.alias) { mutableStateOf<String?>(null) }
    val selectedPackages = category.effectivePackages(
        explicitPackageToCategory,
        autoOtherPackages,
        installedPackageNames
    ).toSet()
    val filteredApps = remember(
        installedApps,
        searchText,
        selectedPackages,
        currentCategoryLabels,
        selectedCategoryFilter
    ) {
        val query = searchText.trim()
        val searchedApps = if (query.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        val categoryFilteredApps = selectedCategoryFilter?.let { filter ->
            searchedApps.filter { app ->
                val label = currentCategoryLabels[app.packageName]
                if (filter == UNCATEGORIZED_FILTER_LABEL) {
                    label == null
                } else {
                    label?.split(CATEGORY_LABEL_SEPARATOR)?.contains(filter) == true
                }
            }
        } ?: searchedApps

        categoryFilteredApps.sortedByDescending { it.packageName in selectedPackages }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(onClick = { showManualDialog = true }) {
                        Text("手动添加")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!category.isSystem) {
                        OutlinedButton(onClick = onEditCategory) {
                            Text("编辑分类")
                        }
                        OutlinedButton(onClick = onDeleteCategory) {
                            Text("删除分类")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = searchText,
                    onValueChange = onSearchChange,
                    label = { Text("搜索已安装应用") },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    },
                    singleLine = true
                )

                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = { showCategoryFilterMenu = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .offset(y = 2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onPrimary)
                                    )
                                }
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = showCategoryFilterMenu,
                        onDismissRequest = { showCategoryFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部") },
                            onClick = {
                                selectedCategoryFilter = null
                                showCategoryFilterMenu = false
                            }
                        )
                        categoryFilterOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedCategoryFilter = option
                                    showCategoryFilterMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(UNCATEGORIZED_FILTER_LABEL) },
                            onClick = {
                                selectedCategoryFilter = UNCATEGORIZED_FILTER_LABEL
                                showCategoryFilterMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (installedApps.isEmpty()) {
            item {
                Text(
                    text = "没有读取到已安装应用。可点击右上角手动添加包名。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredApps, key = { it.componentName }) { app ->
                AppToggleRow(
                    app = app,
                    categoryLabel = currentCategoryLabels[app.packageName] ?: UNCATEGORIZED_FILTER_LABEL,
                    enabled = app.packageName !in selectedPackages ||
                            (app.packageName in category.packages &&
                                    savedPackageToCategory[app.packageName] != category.alias),
                    checked = app.packageName in selectedPackages,
                    onCheckedChange = { onTogglePackage(app.packageName, it) }
                )
            }
        }
    }

    if (showManualDialog) {
        ManualPackageDialog(
            packageName = manualPackage,
            onPackageNameChange = onManualPackageChange,
            onDismiss = { showManualDialog = false },
            onConfirm = {
                onAddManualPackage()
                showManualDialog = false
            }
        )
    }
}

@Composable
private fun AppToggleRow(
    app: InstalledApp,
    categoryLabel: String,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = if (enabled) {
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
        } else {
            Modifier.fillMaxWidth()
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )

            Spacer(Modifier.width(8.dp))

            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = app.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = categoryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    text = app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ManualPackageDialog(
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加包名") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = packageName,
                onValueChange = onPackageNameChange,
                label = { Text("包名") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                enabled = packageName.isNotBlank(),
                onClick = onConfirm
            ) {
                Text("加入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
