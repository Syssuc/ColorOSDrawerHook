package com.nenbucy.colorosdrawerhook.model

import com.nenbucy.colorosdrawerhook.DrawerCategoryRule
import com.nenbucy.colorosdrawerhook.DrawerConfig
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
data class EditableCategory(
    val alias: String,
    val title: String,
    val packages: List<String>,
    val nativePackages: List<String> = emptyList(),
    val isSystem: Boolean = false
)

internal val OFFICIAL_CATEGORIES = listOf(
    EditableCategory("communicate", "社交通讯", emptyList(), isSystem = true),
    EditableCategory("tools", "工具", emptyList(), isSystem = true),
    EditableCategory("photos", "拍摄美化", emptyList(), isSystem = true),
    EditableCategory("entertainment", "影音娱乐", emptyList(), isSystem = true),
    EditableCategory("shopping", "购物与美食", emptyList(), isSystem = true),
    EditableCategory("games", "游戏", emptyList(), isSystem = true),
    EditableCategory("travel", "旅游出行", emptyList(), isSystem = true),
    EditableCategory("health", "生活与健康", emptyList(), isSystem = true),
    EditableCategory("work", "办公", emptyList(), isSystem = true),
    EditableCategory("finance", "金融", emptyList(), isSystem = true),
    EditableCategory("education", "教育学习", emptyList(), isSystem = true),
    EditableCategory("read", "资讯阅读", emptyList(), isSystem = true),
    EditableCategory("carrier", "运营服务", emptyList(), isSystem = true),
    EditableCategory("other", "其他", emptyList(), isSystem = true)
)

internal val OFFICIAL_CATEGORY_ALIASES = OFFICIAL_CATEGORIES
    .map { it.alias }
    .toSet()

internal const val CATEGORY_LABEL_SEPARATOR = "、"
internal const val UNCATEGORIZED_FILTER_LABEL = "未分类"

internal val HIDDEN_DRAWER_PICKER_PACKAGES = setOf(
    "com.android.stk",
    "com.android.stk2",
    "com.coloros.stk",
    "com.coloros.stk2",
    "com.oplus.stk",
    "com.oplus.stk2"
)

data class InstalledApp(
    val label: String,
    val packageName: String,
    val componentName: String,
    val icon: Bitmap
)

internal fun DrawerConfig.toEditableCategories(
    systemCategoryPackages: Map<String, List<String>> = emptyMap()
): List<EditableCategory> {
    val officialByAlias = OFFICIAL_CATEGORIES.associateBy { it.alias }
    val configuredByAlias = categories.associateBy { it.alias }
    val orderedAliases = buildList {
        addAll(categories.map { it.alias }.filter { it !in OFFICIAL_CATEGORY_ALIASES })
        addAll(OFFICIAL_CATEGORIES.map { it.alias })
    }.filter { it.isNotBlank() && it != "others" && it != "suggestion" }.distinct()

    return orderedAliases.mapNotNull { alias ->
        val configured = configuredByAlias[alias]
        val official = officialByAlias[alias]

        when {
            official != null -> official.copy(
                packages = configured?.packages.orEmpty(),
                nativePackages = systemCategoryPackages[alias].orEmpty()
            )

            configured != null -> EditableCategory(
                alias = configured.alias,
                title = configured.title,
                packages = configured.packages,
                nativePackages = systemCategoryPackages[alias].orEmpty()
            )

            else -> null
        }
    }
}

internal fun List<EditableCategory>.explicitPackageToCategory(): Map<String, String> =
    flatMap { category ->
        category.packages.map { packageName ->
            packageName to category.alias
        }
    }.toMap()

internal fun EditableCategory.effectivePackages(
    explicitPackageToCategory: Map<String, String>,
    autoOtherPackages: Set<String>,
    installedPackageNames: Set<String>
): List<String> {
    val nativePackagesForDisplay = if (alias == "suggestion") {
        emptyList()
    } else {
        nativePackages
    }

    val nativePackagesStillHere = nativePackagesForDisplay.filter { packageName ->
        explicitPackageToCategory[packageName] == null ||
                explicitPackageToCategory[packageName] == alias
    }

    val automaticPackages = if (alias == "other") {
        autoOtherPackages
    } else {
        emptySet()
    }

    return (nativePackagesStillHere + automaticPackages + packages)
        .distinct()
        .filter { it in installedPackageNames }
}

internal fun EditableCategory.effectiveAppCount(
    explicitPackageToCategory: Map<String, String>,
    autoOtherPackages: Set<String>,
    installedPackageNames: Set<String>,
    displayApps: List<InstalledApp>
): Int {
    val effectivePackageSet = effectivePackages(
        explicitPackageToCategory,
        autoOtherPackages,
        installedPackageNames
    ).toSet()

    val displayedCount = displayApps.count {
        it.packageName in effectivePackageSet
    }
    val displayedPackageNames = displayApps
        .map { it.packageName }
        .toSet()
    val undisplayedCount = effectivePackageSet.count {
        it !in displayedPackageNames
    }

    return displayedCount + undisplayedCount
}

internal fun generateUniqueCategoryAlias(
    title: String,
    categories: List<EditableCategory>,
    editingIndex: Int?
): String {
    val usedAliases = categories
        .withIndex()
        .filter { (index, _) -> index != editingIndex }
        .map { (_, category) -> category.alias }
        .toSet()
    val base = title.toAliasBase()

    if (base !in usedAliases) {
        return base
    }

    var suffix = 2
    while ("${base}_$suffix" in usedAliases) {
        suffix += 1
    }

    return "${base}_$suffix"
}

internal fun String.toAliasBase(): String {
    val normalized = lowercase(Locale.ROOT)
        .map { char ->
            when {
                char in 'a'..'z' || char in '0'..'9' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')

    return when {
        normalized.isBlank() -> "custom"
        normalized.first().isDigit() -> "c_$normalized"
        else -> normalized
    }
}

internal fun List<EditableCategory>.toDrawerConfig(): DrawerConfig {
    return DrawerConfig(
        categories = map {
            DrawerCategoryRule(
                alias = it.alias.trim(),
                title = it.title.trim(),
                packages = it.packages.map(String::trim).filter(String::isNotBlank).distinct()
            )
        }
    )
}

internal fun DrawerConfig.toPrettyJson(): String {
    val root = JSONObject()
    val categoryArray = JSONArray()

    categories.forEach { category ->
        val categoryObject = JSONObject()
            .put("alias", category.alias)
            .put("title", category.title)

        val packagesArray = JSONArray()
        category.packages.forEach { packagesArray.put(it) }

        categoryObject.put("packages", packagesArray)
        categoryArray.put(categoryObject)
    }

    root.put("categories", categoryArray)

    return root.toString(2)
}

internal fun MutableList<EditableCategory>.assignPackageToCategory(
    targetIndex: Int,
    packageName: String
) {
    val normalizedPackage = packageName.trim()
    if (normalizedPackage.isBlank()) return

    for (index in indices) {
        val category = this[index]
        val packages = category.packages.filter { it != normalizedPackage }
        this[index] = category.copy(packages = packages)
    }

    val target = this[targetIndex]
    this[targetIndex] = target.copy(
        packages = (target.packages + normalizedPackage).distinct()
    )
}
