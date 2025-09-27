package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.manager.RoomManager;
import ru.neverlands.abclient.utils.Russian;

public class ChRoomPhp {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);
        
        // The original C# code modifies the HTML here.
        // The new architecture will parse the data and update a native UI.
        // For now, we call a placeholder processor and return the original array.
        String modifiedHtml = RoomManager.process(html);

        // In the future, we will likely just return the original array
        // and the RoomManager will have updated a ViewModel.
        // For now, to stick closer to the original, we return the (un)modified html.
        return Russian.getBytes(modifiedHtml);
    }
}