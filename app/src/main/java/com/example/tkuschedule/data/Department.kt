package com.example.tkuschedule.data

data class Department(
    val code: String,
    val name: String
) {
    val displayName: String
        get() = "$code　$name"
}