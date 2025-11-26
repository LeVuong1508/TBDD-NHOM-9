package com.example.noteapp.model

import java.io.Serializable

data class Share(
    val id: Int? = null,
    val noteId: Int,
    val fromUserId: Int? = null,
    val toUserId: Int? = null,
    val fromEmail: String? = null,
    val toUserEmail: String? = null, // optional, có thể dùng để hiển thị
    val zipPath: String? = null,
    val status: String? = null, // pending / accepted / rejected
    val title: String? = null,  // title của note
    val createdAt: String? = null
) : Serializable