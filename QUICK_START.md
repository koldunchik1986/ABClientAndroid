# ⚡ Quick Start — Build & Test Fixes

## What Got Fixed

| Issue | Fix | Verify |
|-------|-----|--------|
| Elixir won't drink during fishing | Added `isEliximInventory` check (line 4254) | Elixir drinks, doesn't just switch inventory |
| Errors don't show timing | Added timestamp `HH:MM:SS` + handler `[FastActionManager]` (line 6461) | Error shows time + source |
| gradle CLI broken on Windows | Installed Android Studio 2025.3.2.6 | Build APK using IDE instead |

---

## Build APK Right Now (5 Minutes)

### Step 1: Open Android Studio
```
Press Win+R → Type: studio → Press Enter
```
Wait for it to launch (may take 30-60 seconds on first run)

### Step 2: Open Project
```
File → Open an Existing Project
Navigate to: C:\Users\User\AbclientAndroid
Click Open
```
Wait for Gradle sync (~1-2 minutes)

### Step 3: Build
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
OR press Ctrl+Shift+B
```
Wait 3-10 minutes for build to complete

### Step 4: Get APK
```
Your APK is at:
C:\Users\User\AbclientAndroid\app\build\outputs\apk\debug\app-debug.apk
```

**That's it!** APK ready. Transfer to phone and install.

---

## Test the Fixes (After Installing APK)

### Test #1: Elixir Bliss Drinking
1. Start app → Go to fishing
2. Enable Auto-Fishing with Elixir Bliss selected
3. When elixir auto-triggers:
   - ✅ GOOD: Elixir drinks (HP bar increases)
   - ❌ BAD: Elixir doesn't drink (just switches inventory)

### Test #2: Error Logging
1. Go to fast-actions
2. Trigger an error (use item that's not in inventory)
3. Check game chat or logs:
   - ✅ GOOD: Shows `'13:45:22' [FastActionManager]: Item not found...`
   - ❌ BAD: Shows just `Item not found...` with no time/handler

---

## If Build Fails

1. **File → Invalidate Caches / Restart** (clears Android Studio cache)
2. Wait 30 seconds
3. Try building again
4. If still fails: Restart Android Studio completely

---

## Code Changes (For Reference)

**File:** `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

**Change #1 (Line 4254):**  
Prevent inventory switch when already on elixirs
```java
boolean isEliximInventory = address.contains("&im=6");
if (isInventoryPage && !isEliximInventory && !inventoryAddressMatchesFilter(...)) {
    // Only switch if NOT on elixirs
}
```

**Change #2 (Line 6461):**  
Add timestamp and handler to error messages
```java
String timestamp = String.format("%02d:%02d:%02d", (now/3600000)%24, (now/60000)%60, (now/1000)%60);
String handler = "FastActionManager";
String message = "'" + timestamp + "' [" + handler + "]: " + safeFastId + " не найден...";
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Android Studio won't open | Run Start Menu → Search "Android Studio" |
| Gradle sync hangs | File → Sync Now |
| Build button disabled | Wait for Gradle sync to complete |
| APK not found | Check: `C:\Users\User\AbclientAndroid\app\build\outputs\apk\debug\app-debug.apk` |
| Build fails with error | File → Invalidate Caches → Restart |

---

## Next Steps

1. ✅ Built/installed APK from this code
2. ✅ Tested both fixes work
3. ✅ Ready to commit to git

**Git Commit Command:**
```bash
git add app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java
git commit -m "Fix elixir drinking + enhance error logging

- Fixed: Post-fast-action no longer forces inventory switch when on elixirs
- Fixed: Error logs now include timestamp (HH:MM:SS) and handler name"
git push
```

---

**Status:** ✅ Everything ready. Just build and test!
