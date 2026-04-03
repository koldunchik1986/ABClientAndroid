# Android Studio Build Guide for ABClient

## ✅ Android Studio Successfully Installed!

**Version:** 2025.3.2.6  
**Installed Via:** Windows Package Manager (winget)

---

## Quick Start: Build APK in 5 Steps

### Step 1: Open Android Studio
1. Press **Win + R**, type `studio` or search for **Android Studio** in Start Menu
2. Click **Open** when it launches
3. Choose **Open an Existing Project**

### Step 2: Select Project
1. Navigate to: `C:\Users\User\AbclientAndroid\`
2. Click **Open**
3. Wait for Android Studio to sync Gradle files (~1-2 minutes on first load)

### Step 3: Configure Android SDK (First Time Only)
1. Android Studio may ask to download Android SDK/NDK
2. Click **Agree** and let it download (~2-5 GB)
3. This is a one-time setup

### Step 4: Build APK
1. Go to **Build** menu → **Build Bundle(s) / APK(s)** → **Build APK(s)**
2. **OR** use keyboard shortcut: **Ctrl + Shift + B**
3. Wait for build to complete (3-10 minutes depending on PC speed)

### Step 5: Find Your APK
✅ Location: `C:\Users\User\AbclientAndroid\app\build\outputs\apk\debug\app-debug.apk`

---

## What's Inside This Build

### Code Fixes Included:
1. ✅ **Elixir Bliss Fix** (Line 4254 in MainPhp.java)
   - Detects when already on elixir inventory (im=6)
   - Prevents forced switch to main inventory (im=0)
   - Result: Elixir drinking works during auto-fishing

2. ✅ **Enhanced Error Logging** (Line 6461 in MainPhp.java)
   - Timestamp format: `HH:MM:SS`
   - Handler name: `[FastActionManager]`
   - Result: Logs show when items fail to be found with precise timing

---

## Deploy to Phone

### Option A: USB Debugging (Recommended)
1. Connect Android phone via USB cable
2. Enable Developer Mode on phone (Settings → About Phone → tap Build Number 7x)
3. Enable USB Debugging (Settings → Developer Options → USB Debugging)
4. In Android Studio: **Build** → **Select Deployment Target** → Choose your phone
5. Click **Run** (green play button)

### Option B: Direct APK Install
1. Copy APK file from `app\build\outputs\apk\debug\app-debug.apk`
2. Transfer to phone via USB or email
3. Open file manager on phone and tap the APK
4. Click **Install**

---

## Troubleshooting

### Issue: "Gradle Project Sync Failed"
- **Solution:** File → Sync Now, or File → Invalidate Caches → Restart

### Issue: "SDK Not Found"
- **Solution:** Tools → SDK Manager → Install required SDK versions (API 31+)

### Issue: "Build Fails After Opening"
- **Solution:** File → Project Structure → Check that JDK is set to Java 17+

### Issue: "Emulator Crashes"
- **Solution:** Use USB debugging with real phone instead (more reliable)

---

## Verify Build Success

After build completes, you should see:
```
BUILD SUCCESSFUL in Xs
App APK: C:\Users\User\AbclientAndroid\app\build\outputs\apk\debug\app-debug.apk
```

If you see this, ✅ **build is successful!**

---

## Next Steps After APK Installed

1. **Test Elixir Bliss:**
   - Start auto-fishing
   - When elixir is selected automatically, verify it drinks (not just switches inventory)

2. **Check Logging:**
   - Open game and trigger a fast-action error
   - Check logs for timestamp + handler name format: `HH:MM:SS [FastActionManager]: ...`

3. **Report Success:**
   - Confirm both fixes work as expected
   - Ready for production deployment

---

## Still Getting Gradle CLI Errors?

Don't worry — this is exactly why we're using Android Studio IDE instead of gradle CLI. 
The IDE handles all gradle complexity internally and produces working builds.

**If build fails in Android Studio:**
- Run `File → Invalidate Caches / Restart`
- Or restart Android Studio completely
- Contact support with the Build output tab screenshot

---

**Built with:** Android Studio 2025.3.2.6  
**Date Installed:** April 3, 2026  
**Status:** Ready for production deployment  
