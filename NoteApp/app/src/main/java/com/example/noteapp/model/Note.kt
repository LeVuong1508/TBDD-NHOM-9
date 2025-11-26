package com.example.noteapp.model

import java.io.Serializable

data class Note(
    val id: Int,
    val userId: Int,
    val title: String,
    val content: String,
    val important: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) : Serializable
