package com.anlc.client.data.items

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object ThingParser {

    private val NS: String? = null // Namespace is not used in abthings.xml

    fun parse(context: Context, fileName: String): List<Thing> {
        val things = mutableListOf<Thing>()
        var inputStream: InputStream? = null
        try {
            inputStream = context.assets.open(fileName) // Assuming abthings.xml is in assets folder
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            parser.nextTag() // Advance to the first tag (things)

            parser.require(XmlPullParser.START_TAG, NS, "things")
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                if (parser.name == "t") {
                    things.add(readThing(parser))
                } else {
                    skip(parser)
                }
            }
        } finally {
            inputStream?.close()
        }
        return things
    }

    private fun readThing(parser: XmlPullParser): Thing {
        parser.require(XmlPullParser.START_TAG, NS, "t")
        val image = parser.getAttributeValue(NS, "i") ?: ""
        val name = parser.getAttributeValue(NS, "n") ?: ""
        val description = parser.getAttributeValue(NS, "d")
        val rAttr = parser.getAttributeValue(NS, "r") ?: ""
        val bAttr = parser.getAttributeValue(NS, "b") ?: ""

        val requirements = parseAttributesString(rAttr)
        val bonuses = parseAttributesString(bAttr)

        parser.nextTag() // Advance to the next tag (should be END_TAG of 't')
        parser.require(XmlPullParser.END_TAG, NS, "t")
        return Thing(image, name, description, requirements, bonuses)
    }

    private fun parseAttributesString(attrString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (attrString.isEmpty()) {
            return map
        }
        // Example: "Сила: 5|Ловкость: 1|Урон: 2-3"
        val pairs = attrString.split('|')
        for (pair in pairs) {
            val parts = pair.split(':', limit = 2) // Limit to 2 to handle values with colons
            if (parts.size == 2) {
                map[parts[0].trim()] = parts[1].trim()
            }
        }
        return map
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
