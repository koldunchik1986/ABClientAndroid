# VCode Submission Analysis: FightAuto → fight.Frame → Server

## Summary
This document traces how `fight.Frame` (auto-attack HTML) is submitted to the server, where vcode is used, and any modifications that might occur.

---

## 1. ENTRY POINT: FightAuto.processFight()

**File:** [FightAuto.java](app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java)

**Method:** `processFight(String address, String html, Host host)` (Line 192)

```java
LezFight fight = new LezFight(html);  // ← CREATES FIGHT OBJECT
// ... validation and state checks ...
if (autoFightEnabled && AppVars.Autoboi == AutoboiState.AutoboiOn && fight.IsBoi && ... ) {
    return fight.Frame;  // ← RETURNS HTML (Line 644)
}
```

**Key Point:** `processFight()` returns `fight.Frame` directly without any vcode modification.

---

## 2. VCODE EXTRACTION: LezFight Constructor & Parse()

**File:** [LezFight.java](app/src/main/java/ru/neverlands/abclient/lez/LezFight.java)

**Constructor:** `LezFight(String html)` (Lines 104-107)
```java
public LezFight(String html) {
    _html = html;
    IsValid = Parse(html);  // ← Parses fight_ty, fight_pm, etc.
}
```

**Parse Method:** Extracts `fight_pm` array (Lines 170-189)
```java
// VCode для submit-цепочки берём только из текущего fight_pm[4].
// Зависимости:
// - BuildResult() и BuildFrame() отправляют это значение в `vcode`.
// - сервер валидирует vcode по текущему кадру боя, поэтому кэш из AppVars здесь недопустим.
if (fightpm.length > 4) {
    _vcode = Strip(fightpm[4]);  // ← VCODE EXTRACTED FROM fight_pm[4]
} else {
    _vcode = "";
}
```

**Important:** VCode is taken **directly** from `fight_pm[4]` with only trailing whitespace stripped. NO modification or replacement happens here.

---

## 3. VCODE IN RESULT: BuildResult()

**File:** [LezFight.java](app/src/main/java/ru/neverlands/abclient/lez/LezFight.java)

**Method:** `BuildResult()` (Lines 717-776)

```java
private void BuildResult() {
    String vcode = _vcode != null ? _vcode : "";
    // C# parity: enemy/group/inf_bot берутся ИСХОДНЫМИ значениями из fight_pm (без Strip).
    String enemy = _fightpm.length > 5 ? _fightpm[5] : "";
    String group = _fightpm.length > 6 ? _fightpm[6] : "";
    String infbot = _fightpm.length > 7 ? _fightpm[7] : "";
    String ftrRaw = (_fightty != null && _fightty.length > 2) ? _fightty[2] : String.valueOf(_ftype);
    
    // ... build inu/inb/ina fields ...
    
    StringBuilder sb = new StringBuilder();
    sb.append(vcode).append("|")                    // ← VCODE FIRST TOKEN
            .append(enemy).append("|")
            .append(group).append("|")
            .append(infbot).append("|")
            .append(_levbot).append("|")
            .append(ftrRaw).append("|")
            .append(inputu).append("|")
            .append(inputb).append("|")
            .append(inputa);
    Result = sb.toString();  // ← Format: "vcode|enemy|group|infbot|levbot|ftr|inu|inb|ina"
}
```

**Result Format:** `vcode|enemy|group|inf_bot|lev_bot|ftr|inu|inb|ina`

**Important:** VCode is the first token. It is NOT modified or replaced here.

---

## 4. VCODE IN FRAME: BuildFrame()

**File:** [LezFight.java](app/src/main/java/ru/neverlands/abclient/lez/LezFight.java)

**Method:** `BuildFrame()` (Lines 778-921)

```java
private void BuildFrame() {
    if (_fightpm == null || _fightpm.length < 11) return;
    
    StringBuilder sb = new StringBuilder();
    sb.append("<html><head>...</head><body>");
    sb.append("<form action=\"main.php\" method=POST name=ff>");
    
    sb.append("<input name=post_id type=hidden value=\"7\">");
    
    String enemy = _fightpm.length > 5 ? _fightpm[5] : "";
    String group = _fightpm.length > 6 ? _fightpm[6] : "";
    String infbot = _fightpm.length > 7 ? _fightpm[7] : "";
    String infzb = _fightpm.length > 10 ? _fightpm[10] : "";
    String ftrRaw = (_fightty != null && _fightty.length > 2) ? _fightty[2] : String.valueOf(_ftype);
    
    sb.append("<input name=vcode type=hidden value=\"").append(_vcode).append("\">");  // ← VCODE INPUT (Line 822)
    sb.append("<input name=enemy type=hidden value=\"").append(enemy).append("\">");
    sb.append("<input name=group type=hidden value=\"").append(group).append("\">");
    sb.append("<input name=inf_bot type=hidden value=\"").append(infbot).append("\">");
    sb.append("<input name=inf_zb type=hidden value=\"").append(infzb).append("\">");
    sb.append("<input name=lev_bot type=hidden value=\"").append(_levbot).append("\">");
    sb.append("<input name=ftr type=hidden value=\"").append(ftrRaw).append("\">");
    
    // ... inu/inb/ina fields ...
    
    sb.append("</form>");
    sb.append("<script language=\"JavaScript\">");
    sb.append("var __abSubmitted=false;");
    sb.append("function __abSubmit(reason}{...}");
    sb.append("setTimeout(function(){ __abSubmit('timer'); }, ").append(delay).append(");");  // Auto-submit after delay
    // Event handlers for beforeunload, pagehide, visibility change, click
    sb.append("</script></body></html>");
    
    Frame = sb.toString();  // ← HTML WITH VCODE IN HIDDEN INPUT
}
```

**Key Points:**
- Form posts to `main.php` with `method=POST`
- `post_id=7` (fight attack action)
- Hidden input `vcode` contains `_vcode` directly (NOT modified)
- Auto-submit via JavaScript after configurable delay (1-2 seconds)
- Fallback reload URL includes fresh vcode from SessionManager (Lines 903-911)

---

## 5. VCODE MODIFICATIONS (FALLBACK ONLY)

**File:** [LezFight.java](app/src/main/java/ru/neverlands/abclient/lez/LezFight.java)

**Location:** Lines 903-911 (Fallback reload URL)

```java
String fallbackReloadUrl;
if (AppVars.Profile != null && AppVars.Profile.SkinAuto) {
    fallbackReloadUrl = "main.php?r=" + System.currentTimeMillis();
} else {
    fallbackReloadUrl = "main.php?get_id=56&act=10&go=inf";
    // ✅ SessionManager: получаем валидный vcode для fallback reload
    String vcode = SessionManager.getInstance().getValidVCodeForAction("fight_fallback");
    if (vcode != null && !vcode.isEmpty()) {
        fallbackReloadUrl += "&vcode=" + vcode;  // ← FRESH VCODE FROM SessionManager
    } else {
        Log.w("LezFight", "⚠️ SessionManager: vcode not available for fight fallback reload");
    }
}
```

**Important Finding:** The fallback reload URL (watchdog that prevents the WebView from hanging) uses a FRESH vcode from `SessionManager`, NOT the Fight's vcode. This is correct behavior because:
- The fallback/watchdog might execute much later (1.8-2.8 seconds)
- By then, the original vcode may have expired
- SessionManager ensures we get a valid, fresh vcode for the fallback

---

## 6. SUBMISSION FLOW: Fight.Frame → WebView → Server

### 6a. HTML Returned to WebView
**File:** [FightAuto.java](app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java) (Line 644)

The `fight.Frame` HTML string is returned from `processFight()` and becomes the HTTP response body.

### 6b. WebView Loading
**Path:** HTTP Response (from post-filter) → WebView `loadData()` or `loadUrl()`

The HTML is loaded into WebView via the normal response flow. No code explicitly loads it; it's returned by the post-filter and becomes the content of the WebView.

### 6c. JavaScript Auto-Submit
**In fight.Frame HTML** (lines 826-854):

```javascript
var __abSubmitted=false;
function __abSubmit(reason){
    if(__abSubmitted){return;}
    __abSubmitted=true;
    try{
        console.log('ABCLIENT_AUTOBATTLE_SUBMIT'+(reason?('_'+reason):''));
        if(document&&document.ff&&typeof document.ff.submit==='function'){
            document.ff.submit();  // ← SUBMITS FORM WITH VCODE
        }
    }catch(e){
        console.log('ABCLIENT_AUTOBATTLE_SUBMIT_ERR:'+e);
    }
}

// Timer-based submit after delay (1-2 seconds)
setTimeout(function(){ __abSubmit('timer'); }, 1234);

// Fallback submits on page events
window.addEventListener('beforeunload', function(){ __abSubmit('beforeunload'); });
// ... more event handlers ...
```

**Result:** Form is POST'd to `main.php` with all hidden fields including original `vcode`.

---

## 7. ALTERNATIVE SUBMISSION PATH: FightViewModel

**File:** [FightViewModel.java](app/src/main/java/ru/neverlands/abclient/ui/viewmodel/FightViewModel.java)

**Method:** `processFightHtml()` (Lines 42-137)

This is an alternative path (via JS bridge) that:
1. Parses fight HTML directly in Java
2. Creates a new `LezFight` object
3. Extracts `fight.Result` (the pipe-delimited string with vcode as first token)
4. Posts the Result via `_submitAction.postValue(fight.Result)`
5. JavaScript receives it and submits via form

**Important:** This path also uses `fight.Result` which contains the original vcode from `fight_pm[4]`.

---

## 8. FINAL SUBMISSION TO SERVER

**POST Data:** Form submission to `main.php`

```
POST /main.php
post_id=7
vcode=<ORIGINAL_FROM_fight_pm[4]>
enemy=<enemy>
group=<group>
inf_bot=<inf_bot>
inf_zb=<inf_zb>
lev_bot=<lev_bot>
ftr=<ftr>
inu=<hit_actions>
inb=<block_action>
ina=<magic_actions>
```

**VCode Value:** Same as in `fight_pm[4]`, extracted at the beginning and used throughout without modification.

---

## 9. VCODE SOURCE & VALIDATION

### Where VCode Comes From
1. **Server Response:** VCode is embedded in fight HTML in `var fight_pm = [magmax, odmax, ..., vcode, ...]`
2. **Extraction:** `LezFight.Parse()` extracts it from index 4
3. **Usage:** Both `BuildResult()` and `BuildFrame()` use this same vcode

### Validation Rules
- **For Fight Submission:** Uses vcode from `fight_pm[4]` (current fight frame)
- **For Fallback Reload:** Uses fresh vcode from `SessionManager` (prevents timeout)
- **No Replacement:** Original vcode is NEVER replaced, only appended with fresh vcode for fallback

### Potential Issues
If vcode in `fight_pm[4]` is invalid or missing:
1. The attack will still be sent with invalid/empty vcode
2. Server will reject it with "Неверный код защиты" error
3. Fallback reload might succeed if SessionManager has valid vcode

---

## 10. CHECKPOINT SUMMARY

| Component | VCode Source | VCode Modified? | File | Line |
|-----------|-------------|-----------------|------|------|
| LezFight.Parse() | fight_pm[4] | NO | LezFight.java | 181 |
| LezFight.BuildResult() | _vcode | NO | LezFight.java | 721 |
| LezFight.BuildFrame() | _vcode | NO | LezFight.java | 822 |
| Fallback Reload | SessionManager | NEW | LezFight.java | 907 |
| FightAuto.processFight() | fight.Frame | NO | FightAuto.java | 644 |
| WebView Submission | Hidden input | NO | Browser | JavaScript |
| Server Receives | POST vcode | Original | main.php | post_id=7 |

---

## 11. CRITICAL FINDING

**VCode is NOT replaced or modified between extraction and submission.**

### The flow is:
```
fight_pm[4] 
    ↓ (Strip whitespace only)
_vcode 
    ↓ (Use as-is in BuildResult/BuildFrame)
fight.Result & fight.Frame 
    ↓ (Posted to server with original vcode)
main.php receives post_id=7 with original vcode
```

### If vcode is stale or invalid:
- The original fight.Frame will send an old vcode
- The fallback reload will try refreshing with a fresh vcode from SessionManager
- But the main attack POST will always send: `vcode=<original from fight_pm[4]>`

---

## 12. RECOMMENDATIONS FOR DEBUGGING

If you suspect vcode issues in fight submissions:

1. **Check fight_pm[4] value in HTML:**
   ```
   Check logs: "processFight HTML dump" for var fight_pm content
   ```

2. **Verify _vcode extraction:**
   ```
   Check LezFight constructor logs: "fight_pm: vcode=..."
   ```

3. **Confirm no modification in BuildFrame:**
   ```
   Check BuildFrame logs: includes captured vcode in debug output
   ```

4. **Monitor SessionManager for fallback vcode:**
   ```
   Check logs: "SessionManager: vcode not available for fight fallback reload"
   or "FIGHT_CACHE: using cached vcode from fight start"
   ```

5. **Check actual POST data:**
   ```
   Monitor HTTP logs for POST /main.php with post_id=7
   Verify vcode parameter matches fight_pm[4]
   ```
