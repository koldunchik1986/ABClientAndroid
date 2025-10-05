
package ru.neverlands.abclient.manager;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.repository.ApiRepository;
import ru.neverlands.abclient.utils.CustomDebugLogger;

public class ContactsManager {

    private static final String TAG = "ContactsManager";
    private static final String CONTACTS_FILE_NAME = "contacts.xml";
    private static final ConcurrentHashMap<String, Contact> contactsCache = new ConcurrentHashMap<>();
    private static File contactsFile;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public interface ContactOperationCallback {
        void onSuccess(Contact contact);
        void onFailure(String message);
    }

    public interface LoadContactsCallback {
        void onSuccess(List<Contact> contacts);
        void onFailure(String message);
    }

    public static void initialize(Context context) {
        File filesDir = context.getExternalFilesDir(null);
        if (filesDir != null) {
            contactsFile = new File(filesDir, CONTACTS_FILE_NAME);
            if (!contactsFile.exists()) {
                copyDefaultContactsFromAssets(context);
            }
            loadContactsFromXml();
        }
    }

    private static void copyDefaultContactsFromAssets(Context context) {
        AssetManager assetManager = context.getAssets();
        try (InputStream in = assetManager.open(CONTACTS_FILE_NAME);
             OutputStream out = new FileOutputStream(contactsFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            Log.i(TAG, "Default contacts.xml copied to " + contactsFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy default contacts.xml", e);
        }
    }

    private static void loadContactsFromXml() {
        if (contactsFile == null || !contactsFile.exists()) {
            return;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(contactsFile);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("contact");
            contactsCache.clear();

            for (int i = 0; i < nList.getLength(); i++) {
                Node node = nList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    Contact contact = new Contact();
                    contact.playerID = getTagValue("playerID", element);
                    contact.nick = getTagValue("nick", element);
                    contact.playerLevel = Integer.parseInt(getTagValue("playerLevel", element, "0"));
                    contact.inclination = Integer.parseInt(getTagValue("inclination", element, "0"));
                    contact.inclinationName = getTagValue("inclinationName", element);
                    contact.clanNumber = getTagValue("clanNumber", element);
                    contact.clanIco = getTagValue("clanIco", element);
                    contact.clanName = getTagValue("clanName", element);
                    contact.clanStatus = getTagValue("clanStatus", element);
                    contact.gender = Integer.parseInt(getTagValue("gender", element, "0"));
                    contact.blockStatus = Integer.parseInt(getTagValue("blockStatus", element, "0"));
                    contact.jailStatus = Integer.parseInt(getTagValue("jailStatus", element, "0"));
                    contact.muteSeconds = Integer.parseInt(getTagValue("muteSeconds", element, "0"));
                    contact.muteForumSeconds = Integer.parseInt(getTagValue("muteForumSeconds", element, "0"));
                    contact.onlineStatus = Integer.parseInt(getTagValue("onlineStatus", element, "0"));
                    contact.geoLocation = getTagValue("geoLocation", element);
                    contact.warLogNumber = getTagValue("warLogNumber", element);
                    contact.classId = Integer.parseInt(getTagValue("classId", element, "0"));
                    contact.comment = getTagValue("comment", element);
                    contactsCache.put(contact.nick, contact);
                }
            }
            Log.i(TAG, "Contacts loaded from XML into cache.");
        } catch (Exception e) {
            Log.e(TAG, "Error loading contacts from XML", e);
        }
    }

    private static void saveContactsToXml() {
        if (contactsFile == null) {
            return;
        }
        executor.execute(() -> {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.newDocument();

                Element rootElement = doc.createElement("contacts");
                doc.appendChild(rootElement);

                for (Contact contact : contactsCache.values()) {
                    Element contactElement = doc.createElement("contact");
                    rootElement.appendChild(contactElement);
                    
                    createChildElement(doc, contactElement, "playerID", contact.playerID);
                    createChildElement(doc, contactElement, "nick", contact.nick);
                    createChildElement(doc, contactElement, "playerLevel", String.valueOf(contact.playerLevel));
                    createChildElement(doc, contactElement, "inclination", String.valueOf(contact.inclination));
                    createChildElement(doc, contactElement, "inclinationName", contact.inclinationName);
                    createChildElement(doc, contactElement, "clanNumber", contact.clanNumber);
                    createChildElement(doc, contactElement, "clanIco", contact.clanIco);
                    createChildElement(doc, contactElement, "clanName", contact.clanName);
                    createChildElement(doc, contactElement, "clanStatus", contact.clanStatus);
                    createChildElement(doc, contactElement, "gender", String.valueOf(contact.gender));
                    createChildElement(doc, contactElement, "blockStatus", String.valueOf(contact.blockStatus));
                    createChildElement(doc, contactElement, "jailStatus", String.valueOf(contact.jailStatus));
                    createChildElement(doc, contactElement, "muteSeconds", String.valueOf(contact.muteSeconds));
                    createChildElement(doc, contactElement, "muteForumSeconds", String.valueOf(contact.muteForumSeconds));
                    createChildElement(doc, contactElement, "onlineStatus", String.valueOf(contact.onlineStatus));
                    createChildElement(doc, contactElement, "geoLocation", contact.geoLocation);
                    createChildElement(doc, contactElement, "warLogNumber", contact.warLogNumber);
                    createChildElement(doc, contactElement, "classId", String.valueOf(contact.classId));
                    createChildElement(doc, contactElement, "comment", contact.comment);
                }

                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(contactsFile);
                transformer.transform(source, result);

                Log.i(TAG, "Contacts saved to XML.");

            } catch (Exception e) {
                Log.e(TAG, "Error saving contacts to XML", e);
            }
        });
    }

    public static void addContact(Context context, String nick, final ContactOperationCallback callback) {
        CustomDebugLogger.initialize("add_contact_" + nick + ".txt");
        ApiRepository.getPlayerId(nick, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String serverResponse) {
                handler.postDelayed(() -> {
                    String[] parts = serverResponse.split("\\|");
                    String playerId = parts[0];
                    ApiRepository.getPlayerInfo(playerId, new ApiRepository.ApiCallback<Contact>() {
                        @Override
                        public void onSuccess(Contact contact) {
                            contactsCache.put(contact.nick, contact);
                            saveContactsToXml();
                            handler.post(() -> callback.onSuccess(contact));
                        }

                        @Override
                        public void onFailure(String message) {
                            handler.post(() -> callback.onFailure(message));
                        }
                    });
                }, 3000);
            }

            @Override
            public void onFailure(String message) {
                handler.post(() -> callback.onFailure(message));
            }
        });
    }

    public static void deleteContact(String name) {
        contactsCache.remove(name);
        saveContactsToXml();
    }

    public static void updateContact(Contact contact) {
        if (contact == null || contact.nick == null) return;
        contactsCache.put(contact.nick, contact);
        saveContactsToXml();
    }

    public static void loadContacts(Context context, final LoadContactsCallback callback) {
        // This method now loads from the cache, but we keep the async structure for compatibility with the Activity
        handler.post(() -> callback.onSuccess(new ArrayList<>(contactsCache.values())));
    }

    public static String getClassIdOfContact(String name) {
        Contact contact = contactsCache.get(name);
        int classId = 0;
        if (contact != null) {
            classId = contact.classId;
        }
        Log.d(TAG, "GetClassIdOfContact for '" + name + "' returned " + classId);
        return String.valueOf(classId);
    }

    private static String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag).item(0).getChildNodes();
        Node node = (Node) nodeList.item(0);
        return node != null ? node.getNodeValue() : "";
    }

    private static String getTagValue(String tag, Element element, String defaultValue) {
        try {
            return getTagValue(tag, element);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static void createChildElement(Document doc, Element parent, String tagName, String textContent) {
        if (textContent == null) textContent = "";
        Element element = doc.createElement(tagName);
        element.appendChild(doc.createTextNode(textContent));
        parent.appendChild(element);
    }
}
