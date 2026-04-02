# Fight End Detection Locations in Codebase

## Summary of Fight Ending Detection Points

The codebase detects when a fight has ended and calls `clearFightContext()` to clean up SessionManager's VCode cache in several key locations.

---

## 1. 🔴 PRIMARY FIGHT END DETECTION: FightAuto.java

### File: [app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java](app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java)

### Core Logic (Line 268-279):

```java
boolean fightEnded = !fight.IsBoi && !fight.IsWaitingForNextTurn && !isProbeTransitionalInactiveFrame;
if (fightEnded) {
    host.registerFightEnd(fight);
    host.publishFightResultFromLogsIfNeeded(html, address, fight.LogBoi);
    // Fight ended - SessionManager can return to normal 5-minute timeout
    ru.neverlands.abclient.utils.SessionManager.getInstance().clearFightContext();  // ← CRITICAL
}
```

### Method: **`processFight(FightHost host, String html, String address)`**

**Location:** Lines 250-300 in FightAuto.java

**What triggers fight end detection:**
- `!fight.IsBoi` - Fight is no longer in combat state ("бой" = battle)
- `!fight.IsWaitingForNextTurn` - Not waiting for next turn
- `!isProbeTransitionalInactiveFrame` - Not in a transitional probe frame

### Signal After Fight Completion:

The sequence is:
1. **Line 273:** `host.registerFightEnd(fight)` - Marks fight as ended
2. **Line 274:** `host.publishFightResultFromLogsIfNeeded(...)` - Publishes victory/loot
3. **Line 276:** `SessionManager.getInstance().clearFightContext()` - **Clears fight cache** ✅

---

## 2. 🟡 FIGHT END REGISTRATION: MainPhp.java

### File: [app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java](app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java)

### Method 1: **`registerFightEnd(LezFight fight)`**
- **Location:** Lines 5960-5962
- **Purpose:** Delegates to `registerFightEndByLogId()` with source "fight_frame"
- **Called from:** FightAuto.java line 273

### Method 2: **`registerFightEndByLogId(String logId, String source)`**
- **Location:** Lines 5969-5987
- **Purpose:** Unified fight completion accounting with deduplication
- **Key Actions:**
  - Updates `AppVars.LastBoiEndLog` to prevent duplicate counting
  - Calls `ChatStats.addFight()` (increments fight counter)
  - **CRITICAL:** Clears `AppVars.LastFightPulseAtMs = 0L` (prevents fishing from thinking fight still active)
  - Logs with `FileLogger.trace()`

### Deduplication Logic:
```java
if (!logId.equals(AppVars.LastBoiEndLog)) {
    AppVars.LastBoiEndLog = logId;              // Update last recorded fight
    ru.neverlands.abclient.utils.ChatStats.addFight();
    AppVars.LastFightPulseAtMs = 0L;           // CRITICAL: Clear fight pulse
    // ... logging ...
}
```

---

## 3. 🟢 FEXP ARRAY - INDICATES FIGHT RESULTS

### Where `fexp` is Used (Indicates Fight End Response):

The `fexp` array appears in JavaScript on the fight completion page. It contains fight result data:

- **fexp[0]** - Fight experience value
- **fexp[1]** - Fight result (fres)
- **fexp[3]** - VCode token
- **fexp[4]** - Captcha token (code.php?{token})
- **fexp[5]** - Fight type
- **fexp[6]** - Additional status

### Extraction Method: **`extractCaptchaUrlFromFexp(String html)`**

**Location:** [MainPhp.java](MainPhp.java) Lines 731-751

**What it does:**
- Extracts `var fexp = [...]` from HTML
- Builds captcha URL from `fexp[4]`: `"http://neverlands.ru/modules/code/code.php?" + fexp[4]`
- Used to detect when fight has captcha on completion page

**Presence of `fexp` array = Fight ended, results available**

---

## 4. 🔵 FIGHT FINISH LINK EXTRACTION

### Method: **`extractFightFinishLinkFromHtml(String html, boolean withCaptchaPlaceholder)`**

**Location:** [MainPhp.java](MainPhp.java) Lines 761-780

**Purpose:** Builds the fight completion URL: `main.php?get_id=61&act=7&fexp=...&fres=...&vcode=...`

**Detection Strategy:**
1. First tries direct link match: `get_id=61&act=7&fexp=`
2. Falls back to parsing `var fexp = [...]` array
3. Constructs URL with extracted parameters

**Pattern Indicators of Fight End:**
- `get_id=61` - Fight completion action
- `act=7` - Action code 7 (completion)
- `fexp=[...]` - Fight result data present
- `fres` - Fight result field

---

## 5. 📊 FIGHT FINISH PAGE MARKERS

### Method: **`inspectFightFinishPageMarkers(String html, FightHost host)`**

**Location:** [FightAuto.java](FightAuto.java) (referenced in line 266)

**Uses these markers to detect fight end:**
- `hasFendForm` - FORM with name="FEND" present
- `hasCodeInput` - Captcha input field present
- `hasCaptchaImage` - Captcha image (`code.php?`) present
- `hasFkeyScript` - JavaScript for key blocking present

**Presence of these = Fight results HTML received**

---

## 6. 🟣 PROBE TRANSITIONAL FRAME DETECTION

### Purpose: Filter false-positives

**Lines 254-267 in FightAuto.java:**

```java
boolean isProbeTransitionalInactiveFrame = autoFightProbeAddress
        && !fight.IsBoi
        && !fight.IsWaitingForNextTurn
        && (resolvedFightCaptchaUrl == null || resolvedFightCaptchaUrl.isEmpty())
        && !finishMarkers.hasFendForm
        && !finishMarkers.hasCodeInput
        && !finishMarkers.hasCaptchaImage
        && finishMarkers.hasFkeyScript
        && host.isFightFrameHtml(html);
```

**This prevents clearing VCode on transitional probe frames that look like they might be fight end but aren't actually.**

---

## Summary of Key Methods & Their Roles

| File | Method | Purpose | Fight-End Signal |
|------|--------|---------|------------------|
| FightAuto.java | processFight() | Main fight processing loop | Detects `!IsBoi && !IsWaitingForNextTurn` |
| FightAuto.java | processFight() | clearFightContext() call | **Clears VCode cache** ✅ |
| MainPhp.java | registerFightEnd() | Records fight completion | Calls registerFightEndByLogId() |
| MainPhp.java | registerFightEndByLogId() | Unified accounting | Clears LastFightPulseAtMs, adds to stats |
| MainPhp.java | extractCaptchaUrlFromFexp() | Parses `fexp[4]` | Captcha URL = fight results available |
| MainPhp.java | extractFightFinishLinkFromHtml() | Builds completion URL | `get_id=61&act=7&fexp=...` = fight end |
| MainPhp.java | publishFightResultFromLogsIfNeeded() | Victory/loot reporting | Logs fight results |

---

## Critical Code Locations for VCode Cleanup

### ✅ Current Implementation - CORRECT

**Location: [FightAuto.java Line 276](FightAuto.java#L276)**

```java
// Fight ended - SessionManager can return to normal 5-minute timeout
ru.neverlands.abclient.utils.SessionManager.getInstance().clearFightContext();
```

**This is where clearFightContext() is properly called when fight ends.**

### Verification Checklist

- [x] Fight end detection: `!fight.IsBoi && !fight.IsWaitingForNextTurn`
- [x] registerFightEnd() called to mark completion in statistics
- [x] publishFightResultFromLogsIfNeeded() called for victory/loot
- [x] clearFightContext() called to reset VCode cache from 120s (FIGHT_FALLBACK_MODE) back to 30s
- [x] Logging with FileLogger.trace() for debugging

---

## How Fight Ending Works

### Sequence:

```
1. Fight page received
   ↓
2. FightAuto.processFight() parses LezFight object
   ↓
3. Check: !fight.IsBoi && !fight.IsWaitingForNextTurn
   ↓
4. If true → FIGHT ENDED
   ↓
5. registerFightEnd(fight) → MainPhp.registerFightEndByLogId()
   ↓
6. publishFightResultFromLogsIfNeeded() → Victory/loot message
   ↓
7. SessionManager.clearFightContext() ← CLEARS VCODE CACHE ✅
   ↓
8. VCode timeout returns from 120s → 30s
   ↓
9. Fishing/auto-functions resume normally
```

---

## Related Variables

- **AppVars.LastFightPulseAtMs** - Cleared to 0 when fight ends (prevents fishing from thinking fight still active)
- **AppVars.LastBoiEndLog** - Tracks last ended fight ID (prevents duplicate counting)
- **fight.IsBoi** - True while in combat, False when fight frame but not in active combat
- **fight.IsWaitingForNextTurn** - True if waiting for next turn input, False when results showing
- **AppVars.LastBoiLog** - Current fight's log ID (used for victory/loot detection)

---

## Notes

- `clearFightContext()` is **REQUIRED** to transition from FIGHT_FALLBACK_MODE (120s VCode timeout) back to normal mode (30s timeout)
- Without this call, VCode would expire after 120s instead of being properly invalidated when fight ends
- The location is currently correct in FightAuto.java line 276
- Deduplication in `registerFightEndByLogId()` prevents counting the same fight multiple times if the page reloads
