package com.github.tvbox.newbox.domain

data class VodItem(
    val id: String,
    val name: String,
    val pic: String = "",
    val type: String = "",
    val year: String = "",
    val area: String = "",
    val note: String = "",
    val last: String = "",
    val sourceKey: String = "",
)
