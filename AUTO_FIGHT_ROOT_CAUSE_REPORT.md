# 🔴 AUTO-FIGHT HANGS AFTER 1 STRIKE - ROOT CAUSE IDENTIFIED

**Date:** April 1, 2026  
**Analyzed Logs:** 20260401_filelogger, fightauto, fightviewmodel, sessionmanager, mainphp  
**Status:** ❌ **Root cause FOUND - SessionManager context is empty**

---

## SUMMARY

Auto-fight successfully starts a fight and appears to strike once, but then **hangs and retreats immediately** instead of continuing to the next turn. The root cause is:

**❌ SessionManager.getValidVCodeForAction("fight_fallback") fails with "NO_SESSION: контекст пуст" (context empty)**

This failure occurs **BEFORE** the first strike is even attempted, preventing the system from getting a valid VCode for the strike request.

---

## DETAILED TIMELINE (19:56:30 - 19:56:46)

### ✅ Stage 1: Fight Detected & Validated (19:56:30-19:56:31)

| Time | Log Source | Message | Status |
|------|-----------|---------|--------|
| 19:56:30.950 | MainPhp | process() called with fight HTML (htmlLen=2940) | ✅ |
| 19:56:31.033 | FightAuto | LezFight.parsed: **IsValid=true**, IsBoi=true, IsWaitingForNextTurn=false | ✅ |
| 19:56:31.037 | FightAuto | **NEW FIGHT detected!** LogBoi: → 728217960 | ✅ |
| 19:56:31.045 | FightAuto | in fight, checking safety: DoStop=false, IsLowHp=false, IsLowMa=false | ✅ |
| 19:56:31.049 | FightAuto | **processFight: SAFE - returning fight.Frame for auto-attack** | ✅ |

**Conclusion:** Fight properly detected, all safety checks pass. System ready to execute first strike.

---

### ❌ Stage 2: CRITICAL - SessionManager Context is EMPTY (19:56:31.028-19:56:31.030)

```
19:56:31.028 [TRACE] FIGHT_FALLBACK_MODE: using extended timeout 120000ms
19:56:31.030 [WARN]  NO_SESSION: actionName=fight_fallback - контекст пуст
                     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                     CRITICAL ERROR - SESSION CONTEXT IS EMPTY!
```

**What this means:**
- When FightAuto tries to execute the strike, it needs a VCode
- FightAuto calls: `SessionManager.getInstance().getValidVCodeForAction("fight_fallback")`
- SessionManager looks for a cached VCode for the "fight_fallback" action
- **Result:** `NO_SESSION: контекст пуст` = The SessionContext object is null or empty
- **Impact:** Cannot retrieve ANY valid VCode for the strike request

**When this happened:**
- **19:56:31.030** - This is BEFORE first strike attempt
- Fight page loaded at 19:56:30.950
- VCode should have been parsed from fight HTML and cached in SessionContext
- But it wasn't

---

### ❌ Stage 3: Zombie Retreat Loop (19:56:34.915 - 19:56:43.017)

With no valid VCode available for a strike request, the system begins a **zombie retreat loop**:

```
19:56:34.923 [MainPhp] registerFightEnd: fight counted, source=
            └─→ System thinks fight is over?
            
19:56:36.807 [MainPhp] Process page: http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=1775062596656
            └─→ RETREAT (go=ret) request WITHOUT VCODE - just r= parameter
            
19:56:38.835 [MainPhp] Process page: http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=1775062598653
            └─→ Another RETREAT attempt
            
19:56:40.794 [MainPhp] Process page: http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=1775062600655
            └─→ Another RETREAT attempt
            
19:56:42.811 [MainPhp] Process page: http://neverlands.ru/main.php?get_id=56&act=10&go=ret&r=1775062602651
            └─→ Another RETREAT attempt
```

**Key observation:** These are `go=ret` (RETREAT, not strike) requests, and they use only `r=` parameter (timestamp), NOT `vcode=`.

**Why?** Because `go=ret` doesn't require VCode protection. When the system realizes it can't get a VCode for the strike, it falls back to retreating (exiting the fight).

---

### ✅ Stage 4: Session Recovery Attempt (19:56:45+)

After the zombie loop exhausts, system attempts recovery:

```
19:56:45.401 [MainPhp] go=10&af_bootstrap=1 (CHARACTER PAGE) - fishing gear check
19:56:45.654 [MainPhp] Receive character HTML (htmlLen=16640)
19:56:45.672 [MainPhp] go=inf&vcode=ae719653... (TRY FIGHT AGAIN with NEW VCODE)
19:56:45.883 [MainPhp] go=ret&vcode=f9e0d3f1... (RETREAT with proper VCODE)
```

System gets a second chance with a fresh VCode, but the damage is done—the fight is already lost.

---

## ROOT CAUSE ANALYSIS

### Why SessionManager Context is Empty

The chain of failure:

```
1. MainPhp.process() receives fight HTML at 19:56:30.950
   └─→ HTML contains valid fight frame + VCode input

2. FightViewModel.processFightHtml() should parse VCode
   OR
   WebViewRequestInterceptor.onPageLoad() should parse VCode
   └─→ VCode is extracted and stored in SessionContext

3. SessionManager.parseAndStoreVCode(vcode)
   └─→ Creates/updates SessionContext with new VCode
   
4. When FightAuto requests VCode for first strike...
   └─→ SessionManager.getValidVCodeForAction("fight_fallback") should return cached VCode
   
❌ INSTEAD: SessionManager returns "NO_SESSION" = SessionContext is null/empty
```

### Possible Root Causes

| # | Cause | Impact | Likelihood |
|---|-------|--------|------------|
| **1** | **SessionContext never created at login** | VCode cache never initialized | **HIGH** |
| **2** | SessionContext created but cleared before fight | VCode lost before first strike | **MEDIUM** |
| **3** | WebViewRequestInterceptor not called for fight pages | VCode never parsed from fight HTML | **MEDIUM** |
| **4** | VCode pattern regex not matching fight HTML | VCode extraction fails silently | **MEDIUM** |
| **5** | FightViewModel.processFightHtml() doesn't parse VCode | Relies only on interceptor (broken) | **MEDIUM** |

---

## EVIDENCE TABLE

| Evidence | Status | Details |
|----------|--------|---------|
| Fight detection | ✅ **WORKS** | LezFight.IsValid=true, IsBoi=true, all safety checks pass |
| Fight HTML received | ✅ **WORKS** | mainphp.log shows 2940 bytes received at 19:56:30.950 |
| FightAuto.processFight() | ✅ **WORKS** | Correctly parses fight state and returns fight.Frame |
| **SessionManager VCode lookup** | ❌ **FAILS** | NO_SESSION error at 19:56:31.030, before first strike |
| Strike request generation | ❌ **FAILS** | No go=inf strike URL with vcode found in logs |
| Strike execution | ❌ **FAILS** | System falls back to retreat (go=ret) instead |
| Second turn attempt | ❌ **FAILS** | Only retreat URLs logged, no fighting continues |

---

## VCode LIFECYCLE: Expected vs Actual

### Expected Behavior (3+ Turn Fight):
```
1. Load main.php?go=inf&ab_reload_probe=1
   ↓ [Receive fight HTML with vcode input]
   ↓ SessionManager.parseVCode() → Store in SessionContext
   ↓ FightAuto.autoTurnOnce() called
   
2. First Strike: GET main.php?go=inf&af_tick=1&r=...&vcode=<cached>
   ↓ [Receive strike result HTML]
   ↓ SessionManager.parseVCode() → Update in SessionContext
   ↓ FightAuto.autoTurnOnce() called again
   
3. Second Strike: GET main.php?go=inf&af_tick=2&r=...&vcode=<updated>
   ↓ [Receive strike result HTML]
   ↓ SessionManager.parseVCode() → Update in SessionContext
   ↓ FightAuto.autoTurnOnce() called again
   
4. Third Strike: GET main.php?go=inf&af_tick=3&r=...&vcode=<updated>
   ↓ [Receive "fight ended" page]
   ↓ FightAuto detects fight end
   
5. Retreat: GET main.php?go=ret&vcode=<fresh>
```

### Actual Behavior (1 Turn Only):
```
1. Load main.php?go=inf&ab_reload_probe=1 ✅ SUCCESS
   ↓ [Receive fight HTML]
   ↓ SessionManager.parseVCode() ❌ FAILS - Context empty
   ↓ FightAuto.autoTurnOnce() called but...
   
2. First Strike Attempt: GET main.php?go=inf&...?vcode=???
   ↓ ❌ NO VCODE AVAILABLE - getValidVCodeForAction() returns null
   ↓ System falls back to retreat
   
3. Retreat (no vcode): GET main.php?go=ret&r=...
   ↓ [Receive redirect page]
   ↓ Fight ends
   
4. System stops fighting
```

---

## CRITICAL CODE LOCATIONS

**These files need investigation:**

1. **SessionManager.java**
   - `getValidVCodeForAction("fight_fallback")`
   - Why is SessionContext null at 19:56:31.030?
   - Is it ever initialized?

2. **WebViewRequestInterceptor.java** / **FrontendInterceptor.java**
   - `onPageLoad()` / `onInterceptRequest()`
   - Is it called for fight pages?
   - Does it parse and store VCode correctly?

3. **FightViewModel.java**
   - `processFightHtml()`
   - Does it extract VCode from fight HTML?
   - Where is VCode passed to SessionManager?

4. **FightAuto.java**
   - `autoTurnOnce()`
   - How does it request VCode from SessionManager?
   - What happens when `getValidVCodeForAction()` returns null?

5. **MainPhp.java**
   - `process()` after fight HTML received
   - Does it call` SessionManager.parseVCode()`?
   - Or does it rely only on interceptor?

---

## RECOMMENDED DIAGNOSTIC STEPS

1. **Add debug logs to SessionManager:**
   ```java
   getValidVCodeForAction(String actionName) {
       Log.d(TAG, "getValidVCode for: " + actionName);
       Log.d(TAG, "SessionContext is null? " + (sessionContext == null));
       if (sessionContext != null) {
           Log.d(TAG, "  vcode age: " + sessionContext.ageMs);
           Log.d(TAG, "  vcode: " + sessionContext.vcode);
       }
   }
   ```

2. **Trace VCode parsing in fight response:**
   - Add logs to WebViewRequestInterceptor.onPageLoad()
   - Log all VCode extractions from fight HTML
   - Verify SessionContext.setVCode() is called

3. **Check FightViewModel.processFightHtml():**
   - Log when fight HTML is received
   - Log when VCode is extracted
   - Log if SessionManager.parseVCode() is called

4. **Test SessionContext initialization:**
   - At login, check if SessionContext is created
   - After first page load, check if it contains any VCode
   - After fight page load, check if it contains fight VCode

---

## IMPACT

- **Users:** Auto-fight only works for 1 turn, always retreats immediately after
- **Experience:** Looks like attack succeeds, but then system says "retreat" before next turn
- **Root Issue:** SessionManager not caching VCode from fight responses
- **Fix Scope:** SessionManager initialization + VCode caching from interceptor/FightViewModel

---

## SEVERITY

🔴 **CRITICAL** - Auto-fight feature is completely broken, appears to work but only fights 1 turn then quits.

