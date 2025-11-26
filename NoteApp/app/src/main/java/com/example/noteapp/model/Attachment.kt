package com.example.noteapp.model

import java.io.Serializable

data class Attachment(
    val id: Int? = null,
    val noteId: Int,
    val filePath: String,
    val fileType: String,
    val uploadedAt: String? = null
) : Serializable
