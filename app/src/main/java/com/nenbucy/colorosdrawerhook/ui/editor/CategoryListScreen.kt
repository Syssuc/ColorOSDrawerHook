package com.nenbucy.colorosdrawerhook.ui.editor

import com.nenbucy.colorosdrawerhook.model.EditableCategory
import com.nenbucy.colorosdrawerhook.model.InstalledApp
import com.nenbucy.colorosdrawerhook.model.effectiveAppCount
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
@Composable
internal fun CategoryListScreen(
    categories: List<EditableCategory>,
    explicitPackageToCategory: Map<String, String>,
    autoOtherPackages: Set<String>,
    installedPackageNames: Set<String>,
    displayApps: List<InstalledApp>,
    hideEmptySystemCategories: Boolean,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    if (categories.isEmpty()) {
        EmptyState(onAdd = onAdd)
        return
    }

    val visibleCategoryItems = categories.indices.map { index ->
        val category = categories[index]
        val appCount = category.effectiveAppCount(
            explicitPackageToCategory,
            autoOtherPackages,
            installedPackageNames,
            displayApps
        )

        index to appCount
    }.filter { (_, appCount) ->
        !hideEmptySystemCategories || appCount > 0
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAdd
            ) {
                Text("新增分类")
            }
        }

        items(visibleCategoryItems, key = { it.first }) { (index, appCount) ->
            val category = categories[index]

            CategoryListItem(
                category = category,
                appCount = appCount,
                onOpen = { onOpen(index) },
                onEdit = { onEdit(index) },
                onDelete = { onDelete(index) }
            )
        }
    }
}

@Composable
private fun CategoryListItem(
    category: EditableCategory,
    appCount: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.title.take(1).ifBlank { "#" },
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$appCount 个应用",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "选择应用",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!category.isSystem) {
                    OutlinedButton(onClick = onEdit) {
                        Text("编辑")
                    }
                    OutlinedButton(onClick = onDelete) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "还没有分类",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "先新增一个分类，再为它选择应用。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) {
            Text("新增分类")
        }
    }
}
