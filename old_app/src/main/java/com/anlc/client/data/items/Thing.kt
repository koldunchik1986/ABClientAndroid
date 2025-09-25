package com.anlc.client.data.items

data class Thing(
    val image: String,
    val name: String,
    val description: String?,
    val requirements: Map<String, String>,
    val bonuses: Map<String, String>
)
