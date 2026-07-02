package com.github.tvbox.newbox.domain

data class VodDetail(
    val id: String,
    val name: String,
    val pic: String = "",
    val type: String = "",
    val year: String = "",
    val area: String = "",
    val actor: String = "",
    val director: String = "",
    val description: String = "",
    val seriesFlags: List<String> = emptyList(),
    val seriesMap: Map<String, List<Episode>> = emptyMap(),
    val sourceKey: String = "",
)

data class Episode(
    val name: String,
    val url: String,
)
