package com.dalakoti07.wrtc.ft

data class MessageModel(
    // todo name it enum, and refactor it
    val type: String,
    val name: String? = null,
    val target: String? = null,
    val data: Any? = null
)
