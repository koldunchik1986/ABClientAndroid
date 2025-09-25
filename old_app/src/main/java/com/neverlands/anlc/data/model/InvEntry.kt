package com.neverlands.anlc.data.model

data class InvEntry(
    val html: String,
    val name: String,
    val img: String,
    val properties: String,
    var count: Int = 1
) {
    // In the future, we will add more properties and the CompareTo method here.
}
