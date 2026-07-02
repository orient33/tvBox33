package com.github.tvbox.newbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vod_record")
data class VodRecord(
    @PrimaryKey
    val vodId: String,
    val vodName: String,
    val vodPic: String = "",
    val sourceKey: String = "",
    val sourceName: String = "",
    val lastPlayFlag: String = "",
    val lastPlayIndex: Int = 0,
    val lastPlayProgress: Long = 0,
    val lastPlayTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis(),
)
