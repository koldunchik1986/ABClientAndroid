package com.anlc.client.inventory

data class ParsedDressed(
    val valid: Boolean,
    val wid: String,
    val vcod: String,
    val empty1: Boolean,
    val empty2: Boolean,
    val inRightSlot: Boolean,
    val hand1: String,
    val hand2: String,
    val slist: MutableList<String> = mutableListOf(),
    val dlist: MutableList<String> = mutableListOf()
) {
    // Methods like IsWear1(), IsWear2(), IsWearKnife() will be implemented in a separate manager class
    // that takes ParsedDressed as input, as they depend on AppVars.Profile which is global state.
}
