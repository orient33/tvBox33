package com.github.tvbox.newbox.domain

data class Category(
    val id: String,
    val name: String,
)

data class FilterGroup(
    val key: String,
    val name: String,
    val items: List<FilterItem>,
)

data class FilterItem(
    val key: String,
    val name: String,
    val value: String = key,
)
