package com.github.tvbox.newbox.domain

data class HomeContent(
    val categories: List<Category>,
    val videos: List<VodItem>,
    val filters: Map<String, List<FilterGroup>> = emptyMap(),
)
