package com.github.tvbox.newbox.domain

data class SearchResult(
    val sourceKey: String,
    val sourceName: String,
    val vodItems: List<VodItem>,
)
