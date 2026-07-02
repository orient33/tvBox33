package com.github.tvbox.newbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vod_collect")
data class VodCollect(
    @PrimaryKey
    val vodId: String,
    val vodName: String,
    val vodPic: String = "",
    val sourceKey: String = "",
    val sourceName: String = "",
    val collectTime: Long = System.currentTimeMillis(),
)
