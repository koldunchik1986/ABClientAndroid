package com.anlc.client.data.items

import android.content.Context
import android.util.Log

class ThingManager private constructor() {

    private var things: List<Thing> = emptyList()

    companion object {
        @Volatile
        private var INSTANCE: ThingManager? = null

        fun getInstance(): ThingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThingManager().also { INSTANCE = it }
            }
        }
    }

    fun loadThings(context: Context, fileName: String) {
        if (things.isEmpty()) { // Load only once
            try {
                things = ThingParser.parse(context, fileName)
                Log.d("ThingManager", "Loaded ${things.size} things from $fileName")
            } catch (e: Exception) {
                Log.e("ThingManager", "Error loading things from $fileName", e)
            }
        }
    }

    fun getThingByName(name: String): Thing? {
        return things.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getAllThings(): List<Thing> {
        return things
    }
}
