package com.ishhf.familymap

data class ChatMessage(
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageData: String = "",
    val audioData: String = "",
    val audioDuration: Int = 0,
    val timestamp: Long = 0
)
