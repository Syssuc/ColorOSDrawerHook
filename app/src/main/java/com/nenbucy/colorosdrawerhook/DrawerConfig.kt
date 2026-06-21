package com.nenbucy.colorosdrawerhook

import org.json.JSONObject

data class DrawerCategoryRule(
    val alias: String,
    val title: String,
    val packages: List<String>
)

data class DrawerConfig(
    val categories: List<DrawerCategoryRule>
) {
    val packageToCategory: Map<String, String> =
        categories.flatMap { category ->
            category.packages.map { pkg -> pkg to category.alias }
        }.toMap()

    val categoryTitles: Map<String, String> =
        categories.associate { it.alias to it.title }

    val categoryPackages: Map<String, List<String>> =
        categories.associate { it.alias to it.packages }

    companion object {
        val EMPTY = DrawerConfig(emptyList())

        fun parse(json: String?): DrawerConfig {
            if (json.isNullOrBlank()) return EMPTY

            return runCatching {
                val root = JSONObject(json)

                val categories = mutableListOf<DrawerCategoryRule>()
                val categoryArray = root.optJSONArray("categories")

                if (categoryArray != null) {
                    for (i in 0 until categoryArray.length()) {
                        val obj = categoryArray.getJSONObject(i)

                        val alias = obj.optString("alias").trim()
                        val title = obj.optString("title").trim()
                        val packagesJson = obj.optJSONArray("packages")

                        val packages = mutableListOf<String>()
                        if (packagesJson != null) {
                            for (j in 0 until packagesJson.length()) {
                                val pkg = packagesJson.optString(j).trim()
                                if (pkg.isNotBlank()) {
                                    packages.add(pkg)
                                }
                            }
                        }

                        if (alias.isNotBlank() && title.isNotBlank()) {
                            categories.add(
                                DrawerCategoryRule(
                                    alias = alias,
                                    title = title,
                                    packages = packages
                                )
                            )
                        }
                    }
                }

                DrawerConfig(categories = categories)
            }.getOrElse {
                EMPTY
            }
        }
    }
}
