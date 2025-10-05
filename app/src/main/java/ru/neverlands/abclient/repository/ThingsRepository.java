package ru.neverlands.abclient.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
/*
import ru.neverlands.abclient.db.AppDatabase;
import ru.neverlands.abclient.db.ThingDao;
*/
import ru.neverlands.abclient.model.Thing;

public class ThingsRepository {
    private static final String TAG = "ThingsRepository";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static final ThingsRepository INSTANCE = new ThingsRepository();

    private ThingsRepository() {}

/*
    public void initialize(Context context) {
        executor.execute(() -> {
            ThingDao dao = AppDatabase.getDatabase(context).thingDao();
            int count = dao.getCount();
            if (count == 0) {
                Log.d(TAG, "Things database is empty. Populating from XML...");
                try {
                    InputStream inputStream = context.getAssets().open("abthings.xml");
                    List<Thing> things = parseThingsXml(inputStream);
                    dao.insertAll(things);
                    Log.d(TAG, "Successfully populated " + things.size() + " items into the database.");
                } catch (Exception e) {
                    Log.e(TAG, "Error populating things database", e);
                }
            }
        });
    }
    */

    private List<Thing> parseThingsXml(InputStream inputStream) throws Exception {
        List<Thing> things = new ArrayList<>();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(inputStream, null);

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "t".equals(parser.getName())) {
                String img = parser.getAttributeValue(null, "i");
                String name = parser.getAttributeValue(null, "n");
                String description = parser.getAttributeValue(null, "d");
                String reqStr = parser.getAttributeValue(null, "r");
                String bonStr = parser.getAttributeValue(null, "b");

                if (name != null && !name.isEmpty()) {
                    Thing thing = new Thing();
                    thing.name = name;
                    thing.image = (img != null) ? img : "";
                    thing.description = (description != null) ? description : "";
                    thing.requirements = parseAttributes((reqStr != null) ? reqStr : "");
                    thing.bonuses = parseAttributes((bonStr != null) ? bonStr : "");
                    things.add(thing);
                }
            }
            eventType = parser.next();
        }
        return things;
    }

    private Map<String, String> parseAttributes(String attrString) {
        if (attrString == null || attrString.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        String[] pairs = attrString.split("\\|");
        for (String pair : pairs) {
            String[] parts = pair.split(": ", 2);
            if (parts.length == 2) {
                map.put(parts[0], parts[1].replace("%", ""));
            }
        }
        return map;
    }

/*
    public LiveData<List<Thing>> findByImage(Context context, String image) {
        ThingDao dao = AppDatabase.getDatabase(context).thingDao();
        return dao.findByImage(image);
    }
    */
}
