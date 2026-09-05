package com.balatromodmanager.catalog

data class CatalogFilters(
    val query: String = "",
    val selectedCategory: String? = null,
    val sortMode: CatalogSortMode = CatalogSortMode.DownloadsDesc,
)

enum class CatalogSortMode(val label: String) {
    DownloadsDesc("Downloads (Most)"),
    DownloadsAsc("Downloads (Least)"),
    UpdatedDesc("Updated (Newest)"),
    UpdatedAsc("Updated (Oldest)"),
    NameAsc("Name (A-Z)"),
    NameDesc("Name (Z-A)"),
}

fun List<CatalogMod>.search(filters: CatalogFilters): List<CatalogMod> {
    val category = filters.selectedCategory
    val categoryFiltered = if (category.isNullOrBlank()) {
        this
    } else {
        filter { mod -> mod.categories.any { it.equals(category, ignoreCase = true) } }
    }

    val query = filters.query.trim()
    if (query.isBlank()) {
        return categoryFiltered.sortedFor(filters.sortMode)
    }

    return categoryFiltered
        .mapNotNull { mod ->
            val score = fuzzyScore(mod.searchableText, query)
            if (score == null) null else mod to score
        }
        .sortedWith(
            compareBy<Pair<CatalogMod, FuzzyScore>> { it.second.errors }
                .thenBy { it.second.start }
                .thenBy { it.second.gaps }
                .thenByDescending { it.first.downloadsTotal }
                .thenBy { it.first.title.lowercase() },
        )
        .map { it.first }
}

fun allCategories(mods: List<CatalogMod>): List<String> {
    return mods.flatMap { it.categories }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

private fun List<CatalogMod>.sortedFor(sortMode: CatalogSortMode): List<CatalogMod> {
    return when (sortMode) {
        CatalogSortMode.NameAsc -> sortedBy { it.title.lowercase() }
        CatalogSortMode.NameDesc -> sortedByDescending { it.title.lowercase() }
        CatalogSortMode.UpdatedAsc -> sortedWith(
            compareBy<CatalogMod> { it.lastUpdated }
                .thenBy { it.title.lowercase() },
        )
        CatalogSortMode.UpdatedDesc -> sortedWith(
            compareByDescending<CatalogMod> { it.lastUpdated }
                .thenBy { it.title.lowercase() },
        )
        CatalogSortMode.DownloadsAsc -> sortedWith(
            compareBy<CatalogMod> { it.downloadsTotal }
                .thenBy { it.title.lowercase() },
        )
        CatalogSortMode.DownloadsDesc -> sortedWith(
            compareByDescending<CatalogMod> { it.downloadsTotal }
                .thenByDescending { it.lastUpdated }
                .thenBy { it.title.lowercase() },
        )
    }
}

private data class FuzzyScore(
    val errors: Int,
    val start: Int,
    val gaps: Int,
)

private fun fuzzyScore(text: String, query: String): FuzzyScore? {
    val haystack = text.lowercase()
    val terms = query.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
    var errors = 0
    var start = Int.MAX_VALUE
    var gaps = 0
    for (term in terms) {
        val direct = haystack.indexOf(term)
        if (direct >= 0) {
            start = minOf(start, direct)
            continue
        }
        val subsequence = subsequenceScore(haystack, term) ?: return null
        errors += subsequence.errors
        start = minOf(start, subsequence.start)
        gaps += subsequence.gaps
    }
    return FuzzyScore(errors = errors, start = start.takeIf { it != Int.MAX_VALUE } ?: 0, gaps = gaps)
}

private fun subsequenceScore(text: String, query: String): FuzzyScore? {
    var textIndex = 0
    var gaps = 0
    var start = Int.MAX_VALUE
    for (char in query) {
        val found = text.indexOf(char, startIndex = textIndex)
        if (found < 0) return singleErrorMatch(text, query)
        if (start == Int.MAX_VALUE) start = found
        gaps += found - textIndex
        textIndex = found + 1
    }
    return FuzzyScore(errors = 1, start = start.takeIf { it != Int.MAX_VALUE } ?: 0, gaps = gaps)
}

private fun singleErrorMatch(text: String, query: String): FuzzyScore? {
    val compactWords = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
    compactWords.forEachIndexed { index, word ->
        if (isWithinSingleEdit(word, query)) {
            return FuzzyScore(errors = 1, start = index, gaps = word.length - query.length)
        }
    }
    return null
}

private fun isWithinSingleEdit(word: String, query: String): Boolean {
    if (kotlin.math.abs(word.length - query.length) > 1) return false
    if (word == query) return true
    if (word.length == query.length) {
        val mismatches = word.indices.count { word[it] != query[it] }
        if (mismatches <= 1) return true
        return (0 until word.length - 1).any { index ->
            word[index] == query[index + 1] &&
                word[index + 1] == query[index] &&
                word.removeRange(index, index + 2) == query.removeRange(index, index + 2)
        }
    }
    val shorter = if (word.length < query.length) word else query
    val longer = if (word.length < query.length) query else word
    var mismatchSeen = false
    var shortIndex = 0
    var longIndex = 0
    while (shortIndex < shorter.length && longIndex < longer.length) {
        if (shorter[shortIndex] == longer[longIndex]) {
            shortIndex++
            longIndex++
        } else {
            if (mismatchSeen) return false
            mismatchSeen = true
            longIndex++
        }
    }
    return true
}
