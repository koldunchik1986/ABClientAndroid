package com.anlc.client.inventory

import com.anlc.client.profile.ProfileSettings

class EquippedItemsManager(private val profileSettings: ProfileSettings) {

    var autoFishHand1: String = ""
    var autoFishHand1D: String = ""
    var autoFishHand2: String = ""
    var autoFishHand2D: String = ""
    var autoSkinHand: String = ""
    var autoSkinHandD: String = ""

    /**
     * Checks if an item should be worn in the first hand slot based on profile settings.
     * Mimics C# ParsedDressed.IsWear1()
     */
    fun isWear1(parsedDressed: ParsedDressed): Boolean {
        var isWear1 = false
        // parsedDressed.inRightSlot is determined by the logic here, not directly from parsing
        // For now, we'll keep it as a property of ParsedDressed, but its value will be set here.

        if (!profileSettings.fishAutoWear || profileSettings.fishHandOne.equals("нет", ignoreCase = true)) {
            isWear1 = true
        } else {
            val slistCopy = parsedDressed.slist.toMutableList() // Work on a copy
            val dlistCopy = parsedDressed.dlist.toMutableList()

            if (profileSettings.fishHandOne.equals("Любая удочка", ignoreCase = true)) {
                if (slistCopy.isNotEmpty() && (slistCopy[0].contains("удочка", ignoreCase = true) || slistCopy[0].contains("спиннинг", ignoreCase = true))) {
                    isWear1 = true
                    autoFishHand1 = slistCopy[0]
                    autoFishHand1D = dlistCopy[0]
                    // In C#, items were removed from slist/dlist. Here, we just use the data.
                    // The ParsedDressed object should be treated as immutable input.
                } else if (slistCopy.size > 1 && (slistCopy[1].contains("удочка", ignoreCase = true) || slistCopy[0].contains("спиннинг", ignoreCase = true))) {
                    isWear1 = true
                    // parsedDressed.inRightSlot = true // This would require ParsedDressed to be mutable or return a new one
                    autoFishHand1 = slistCopy[1]
                    autoFishHand1D = dlistCopy[1]
                }
            } else {
                if (slistCopy.isNotEmpty() && slistCopy[0].contains(profileSettings.fishHandOne, ignoreCase = true)) {
                    isWear1 = true
                    autoFishHand1 = slistCopy[0]
                    autoFishHand1D = dlistCopy[0]
                } else if (slistCopy.size > 1 && slistCopy[1].contains(profileSettings.fishHandOne, ignoreCase = true)) {
                    isWear1 = true
                    // parsedDressed.inRightSlot = true
                    autoFishHand1 = slistCopy[1]
                    autoFishHand1D = dlistCopy[1]
                }
            }
        }
        return isWear1
    }

    /**
     * Checks if an item should be worn in the second hand slot based on profile settings.
     * Mimics C# ParsedDressed.IsWear2()
     */
    fun isWear2(parsedDressed: ParsedDressed): Boolean {
        var isWear2 = false
        if (!profileSettings.fishAutoWear || profileSettings.fishHandTwo.equals("нет", ignoreCase = true)) {
            isWear2 = true
        } else {
            val slistCopy = parsedDressed.slist.toMutableList() // Work on a copy
            val dlistCopy = parsedDressed.dlist.toMutableList()

            if (profileSettings.fishHandTwo.equals("Любая удочка", ignoreCase = true)) {
                if (slistCopy.isNotEmpty() && (slistCopy[0].contains("удочка", ignoreCase = true) || slistCopy[0].contains("спиннинг", ignoreCase = true))) {
                    autoFishHand2 = slistCopy[0]
                    autoFishHand2D = dlistCopy[0]
                    isWear2 = true
                } else if (slistCopy.size > 1 && (slistCopy[1].contains("удочка", ignoreCase = true) || slistCopy[0].contains("спиннинг", ignoreCase = true))) {
                    autoFishHand2 = slistCopy[1]
                    autoFishHand2D = dlistCopy[1]
                    isWear2 = true
                }
            }
            else {
                if (slistCopy.isNotEmpty() && slistCopy[0].contains(profileSettings.fishHandTwo, ignoreCase = true)) {
                    autoFishHand2 = slistCopy[0]
                    autoFishHand2D = dlistCopy[0]
                    isWear2 = true
                } else if (slistCopy.size > 1 && slistCopy[1].contains(profileSettings.fishHandTwo, ignoreCase = true)) {
                    autoFishHand2 = slistCopy[1]
                    autoFishHand2D = dlistCopy[1]
                    isWear2 = true
                }
            }
        }
        return isWear2
    }

    /**
     * Checks if a skinning knife is equipped based on profile settings.
     * Mimics C# ParsedDressed.IsWearKnife()
     */
    fun isWearKnife(parsedDressed: ParsedDressed): Boolean {
        val knifeList = listOf(
            "Малый Разделочный Нож", "Охотничий Нож", "Вороненый Охотничий Нож",
            "Разделочный Топорик", "Арисайский Охотничий Нож"
        )

        for (i in parsedDressed.slist.indices) {
            for (j in knifeList.indices) {
                if (parsedDressed.slist[i].contains(knifeList[j], ignoreCase = true)) {
                    if (profileSettings.skinAuto &&
                        (autoSkinHand != parsedDressed.slist[i] || autoSkinHandD != parsedDressed.dlist[i])
                    ) {
                        // In C#, this logged to chat. In Android, this would be an event
                        // or callback to the UI to display a message.
                        // For now, just update the internal state.
                    }
                    autoSkinHand = parsedDressed.slist[i]
                    autoSkinHandD = parsedDressed.dlist[i]
                    return true
                }
            }
        }
        return false
    }
}
