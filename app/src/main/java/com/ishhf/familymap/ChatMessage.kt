package com.ishhf.familymap

data class ChatMessage(
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val timestamp: Long = 0
)
