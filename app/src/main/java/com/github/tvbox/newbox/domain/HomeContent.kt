package com.github.tvbox.newbox.domain

data class HomeContent(
    val categories: List<Category>,
    val videos: List<VodItem>,
    val filters: Map<String, List<FilterGroup>> = emptyMap(),
    val page: String = "1",
    val pageCount: String = "1",
    val total: String = "0",
)
