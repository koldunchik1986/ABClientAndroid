package ru.neverlands.abclient.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import ru.neverlands.abclient.db.AppDatabase
import ru.neverlands.abclient.model.Thing
import java.io.InputStream

object ThingsRepository {
    private const val TAG = "ThingsRepository"

    fun initialize(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(context).thingDao()
            val count = dao.getCount() // This is a blocking call, so it's fine in Dispatchers.IO
            if (count == 0) {
                Log.d(TAG, "Things database is empty. Populating from XML...")
                try {
                    val things = parseThingsXml(context.assets.open("abthings.xml"))
                    dao.insertAll(things) // This is a blocking call
                    Log.d(TAG, "Successfully populated ${things.size} items into the database.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error populating things database", e)
                }
            }
        }
    }

    private fun parseThingsXml(inputStream: InputStream): List<Thing> {
        val things = mutableListOf<Thing>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "t") {
                val img = parser.getAttributeValue(null, "i") ?: ""
                val name = parser.getAttributeValue(null, "n") ?: ""
                val description = parser.getAttributeValue(null, "d") ?: ""
                val reqStr = parser.getAttributeValue(null, "r") ?: ""
                val bonStr = parser.getAttributeValue(null, "b") ?: ""

                if (name.isNotEmpty()) {
                    val thing = Thing(
                        name = name,
                        image = img,
                        description = description,
                        requirements = parseAttributes(reqStr),
                        bonuses = parseAttributes(bonStr)
                    )
                    things.add(thing)
                }
            }
            eventType = parser.next()
        }
        return things
    }

    private fun parseAttributes(attrString: String): Map<String, String> {
        if (attrString.isEmpty()) {
            return emptyMap()
        }
        val map = mutableMapOf<String, String>()
        val pairs = attrString.split('|')
        for (pair in pairs) {
            val parts = pair.split(": ", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = parts[1].removeSuffix("%")
            }
        }
        return map
    }

    fun findByImage(context: Context, image: String): LiveData<List<Thing>> {
        val dao = AppDatabase.getDatabase(context).thingDao()
        return dao.findByImage(image)
    }
}