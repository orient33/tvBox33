package com.github.tvbox.newbox.domain

data class PlayerResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val needSniff: Boolean = false,
)

data class SubtitleTrack(
    val name: String,
    val url: String,
    val language: String = "",
)
