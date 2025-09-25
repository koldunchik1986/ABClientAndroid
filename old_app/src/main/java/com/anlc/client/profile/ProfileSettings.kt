package com.anlc.client.profile

// Placeholder for application profile settings
data class ProfileSettings(
    val fishAutoWear: Boolean = false,
    val fishHandOne: String = "нет",
    val fishHandTwo: String = "нет",
    val skinAuto: Boolean = false,
    val torgTabl: String = "", // Added for TorgList
    val doInvPack: Boolean = false, // Added for inventory grouping
    val doInvSort: Boolean = false // Added for inventory sorting
    // Add other relevant profile settings here as needed
)
