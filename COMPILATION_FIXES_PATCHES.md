# EXACT CODE PATCHES FOR COMPILATION FIXES

## PATCH 1: FileLogger.java - Add clearAllLogs() Method

**File:** `app/src/main/java/ru/neverlands/abclient/utils/FileLogger.java`  
**Location:** Insert after line 63 (after `proxyPoolError()` method)

### Code to Add:

```java
    /**
     * Clears all log files from the Logs directory.
     * This is called when user clicks "Clear Logs" in settings.
     */
    public static void clearAllLogs() {
        IO.execute(() -> {
            try {
                File logsRoot = resolveLogsRoot();
                if (logsRoot == null || !logsRoot.exists()) {
                    Log.w(TAG, "Logs root does not exist, nothing to clear");
                    return;
                }
                
                File[] allFiles = logsRoot.listFiles();
                if (allFiles != null) {
                    for (File file : allFiles) {
                        if (file.isFile()) {
                            if (file.delete()) {
                                Log.d(TAG, "Deleted log file: " + file.getAbsolutePath());
                            } else {
                                Log.w(TAG, "Failed to delete log file: " + file.getAbsolutePath());
                            }
                        } else if (file.isDirectory()) {
                            // Also recursively delete subdirectories like "Critical" and "pool"
                            deleteRecursive(file);
                        }
                    }
                    Log.i(TAG, "All logs cleared successfully");
                } else {
                    Log.w(TAG, "Failed to list files in logs directory");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing all logs", e);
            }
        });
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        if (!fileOrDirectory.delete()) {
            Log.w(TAG, "Failed to delete: " + fileOrDirectory.getAbsolutePath());
        }
    }
```

---

## PATCH 2: ApiRepository.java - Add CustomDebugLogger Import

**File:** `app/src/main/java/ru/neverlands/abclient/repository/ApiRepository.java`  
**Location:** Line 30 (add after the FileLogger import at line 29)

### Search For (lines 24-29):
```java
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.FileLogger;
```

### Replace With:
```java
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.CustomDebugLogger;
import ru.neverlands.abclient.utils.FileLogger;
```

---

## PATCH 3: ChatFilter.java - Add FileLogger Import

**File:** `app/src/main/java/ru/neverlands\abclient\utils\ChatFilter.java`  
**Location:** After line 11

### Search For (lines 8-11):
```java
import ru.neverlands.abclient.manager.ChatUserList;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.model.ChatUser;

public class ChatFilter {
```

### Replace With:
```java
import ru.neverlands.abclient.manager.ChatUserList;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.model.ChatUser;
import ru.neverlands.abclient.utils.FileLogger;

public class ChatFilter {
```

---

## SUMMARY OF CHANGES

| File | Change Type | What | Lines |
|------|-------------|------|-------|
| FileLogger.java | Add Methods | clearAllLogs() + deleteRecursive() | After 63 |
| ApiRepository.java | Add Import | import CustomDebugLogger | Line 30 |
| ChatFilter.java | Add Import | import FileLogger | After 11 |

## Total Files Modified: 3
## Total Errors Fixed: 18
## Build Status After Patches: ✅ SHOULD BUILD SUCCESSFULLY

---

## IMPLEMENTATION STEPS

1. **Open FileLogger.java**
   - Navigate to line 63 (end of proxyPoolError method)
   - Add the clearAllLogs() and deleteRecursive() methods

2. **Open ApiRepository.java**
   - Navigate to line 29 (FileLogger import line)
   - Add CustomDebugLogger import after it

3. **Open ChatFilter.java**
   - Navigate to line 11 (after ChatUser import)
   - Add FileLogger import

4. **Save all files** (ensure UTF-8 encoding, no BOM)

5. **Clean and rebuild:**
   ```
   .\gradlew.bat clean assembleDebug
   ```

---

## VERIFICATION

After applying patches, run:
```
.\gradlew.bat --info assembleDebug 2>&1 | findstr /I "error"
```

Expected output: **0 errors** (only warnings, if any)

If still seeing compilation errors after applying patches, check:
- File encoding is UTF-8 without BOM
- No typos in import statements
- No missing semicolons
- FileLogger.java syntax is correct around new methods
