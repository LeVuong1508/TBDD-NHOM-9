package com.example.noteapp.model

data class User(
    val fullname: String? = null,
    val email: String? = null,
    val password: String? = null,
    val token: String? = null,
    val success: Boolean? = null,
    val message: String? = null
)