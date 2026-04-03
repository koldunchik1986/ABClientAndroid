# TODO: Fishing Overweight Handling + Rod Replacement

## Статус: ✅ PHASE 1, 2, 3 COMPLETED - BUILD SUCCESSFUL

**Глава:** Fishing System  
**Блокирует:** Auto-fishing stability when player inventory fills  
**Источник:** C# ABClient/PostFilter/MainPhp.cs (lines 562-575), MainPhpWear.cs (lines 21-108)  

## Описание проблемы

При достижении ~92% заполнения инвентаря (масса > допустимой):
- Сервер отправляет боевую страницу вместо результата рыбалки
- Система не проверяет массу перед отправкой act=2
- Система не одевает новую удочку когда текущая сломана
- Нет уведомления в чат когда рыбалка остановлена

## План реализации

### Phase 1: Create Parsing Classes

#### [x] Task 1.1: Create GearParser.java
- Parse `slots_inv()` from HTML response
- Extract Hand1, Hand2 (текущая одежда в руках)
- Extract Empty1, Empty2 (есть ли место в руках)
- Extract Wid, Vcod (для снятия одежды)
- Match structure of C# ParsedDressed класса

**Expected Output:**
```java
public class GearParser {
    public String hand1;          // "Телескопическая Облегченная Удочка"
    public String hand2;          // "Слот для оружия в левой руке"
    public boolean empty1;        // false (есть в руке)
    public boolean empty2;        // true (пусто)
    public String wid;            // "27975541"
    public String vcode;          // "787337e6dbe7e7c26bc662c2b8a7eaa"
    public boolean isValid;
    
    public GearParser(String html) { ... }
}
```

#### [x] Task 1.2: Create InventoryParser.java
- Parse inventory table from HTML
- Extract each item: name, wear link
- Return List<InventoryItem> sorted by position
- Match structure of C# GetInvList + InvEntry классов

**Expected Output:**
```java
public class InventoryItem {
    public String name;           // "Телескопическая Облегченная Удочка"
    public String wearLink;       // "main.php?get_id=57&wid=27975541&vcode=..."
    public String durability;     // "8/600"
}

public class InventoryParser {
    public static List<InventoryItem> parseInventory(String html) { ... }
}
```

### Phase 2: Add Overweight Check

#### [x] Task 2.1: Add checkOverweight() method to FishAjaxPhp.java
- Search for overweight pattern: `"<font color=#CC0000>Внимание! Возможен перегруз."`
- Check `AppVars.Profile.FishStopOverWeight` setting
- If both true: call `stopFishingWithMessage("перегруз массы")`
- Log each decision point

**Pseudo-code:**
```java
private static boolean checkOverweight(String html) {
    boolean hasOverweight = html.contains("Внимание! Возможен перегруз.");
    boolean shouldStop = AppVars.Profile.FishStopOverWeight;
    
    if (hasOverweight && shouldStop) {
        FileLogger.trace("[OVERWEIGHT_STOP] Fishing stopped due to mass");
        stopFishingWithMessage("из за перегруза массы");
        return true;  // stop processing
    }
    return false;  // continue
}
```

#### [x] Task 2.2: Call checkOverweight() BEFORE act=2 in processFishAct1()
- Location: Line ~265 `scheduleNoCaptchaAct2Fallback()`
- Add: `if (checkOverweight(lastHtmlResponse)) return;`
- Ensure VCode is fresh before check

### Phase 3: Add Rod Replacement Logic

#### [x] Task 3.1: Add checkAndWearRod() method
- Parse current gear using GearParser
- Check if Hand1 or Hand2 is empty
- If empty and FishAutoWear enabled: find rod in inventory
- Call wearRod(inventoryItem) to execute wear link

**Pseudo-code:**
```java
private static boolean checkAndWearRod(String html) {
    GearParser gear = new GearParser(html);
    if (!gear.isValid) return false;
    
    if (gear.empty1 || gear.empty2) {
        // Find replacement rod
        List<InventoryItem> inventory = InventoryParser.parseInventory(html);
        for (InventoryItem item : inventory) {
            if (item.name.toLowerCase().contains("удочка") || 
                item.name.toLowerCase().contains("спиннинг")) {
                
                // Wear it
                wearRod(item);
                return true;  // Wait for response
            }
        }
        // No rod found - stop fishing
        stopFishingWithMessage("нет удочки в инвентаре");
        return true;
    }
    return false;  // Rod is fine
}
```

#### [x] Task 3.2: Add wearRod(InventoryItem item) method
- Extract WearLink from inventory item
- Build full URL: `current_domain + item.wearLink`
- Send via MainActivityWebView or custom HTTP request
- Wait for response before continuing to act=2

### Phase 4: Integrate Checks into Fishing Flow

#### [x] Task 4.1: Modify processFishAct1() in FishAjaxPhp.java
- After parsing bait list (primid)
- Add overweight check (BEFORE act=2)
- Add rod replacement check (BEFORE act=2)
- Only proceed to act=2 if both checks pass

**Call Order:**
```
1. Parse bait (primid)
2. checkOverweight() - stop if needed
3. checkAndWearRod() - wait if rod needed
4. scheduleNoCaptchaAct2Fallback() - send act=2
```

#### [x] Task 4.2: Add stopFishingWithMessage() method
- Set `AppVars.AutoFishMode = false`
- Send chat message: `[Авто-Рыбалка] Авто-Рыбалка остановлена - {reason}`
- Log with timestamp
- Update UI to disable auto-fish button

### Phase 5: Testing & Validation

#### [ ] Task 5.1: Manual testing - fill inventory to 90%
- Confirm overweight message appears in logs
- Confirm fishing stops with proper chat notification
- Confirm no server force-fight occurs

#### [ ] Task 5.2: Manual testing - equip both rods and break them
- Remove rod from inventory
- Confirm rod replacement logic executes
- Confirm new rod is equipped from inventory
- Confirm fishing continues without interruption

#### [ ] Task 5.3: Test setting toggle
- Disable "Прекращать рыбалку при перегрузе"
- Confirm auto rod replacement happens instead of stop
- Re-enable setting, confirm stop happens instead

#### [ ] Task 5.4: Regression testing
- Confirm normal fishing (no overweight, rods intact) still works
- Confirm lake detection still works (requires lake form)
- Confirm bait selection still works
- Confirm VCode is properly managed

## Dependencies

- `GearParser.java` - BLOCKS Task 3.1
- `InventoryParser.java` - BLOCKS Task 3.1
- Task 2.1 - BLOCKS Task 2.2
- Task 3.1 - BLOCKS Task 3.2
- Task 3.1, 3.2 - BLOCKS Task 4.1

## Files to Create/Modify

**CREATE:**
- `app/src/main/java/ru/neverlands/abclient/utils/GearParser.java`
- `app/src/main/java/ru/neverlands/abclient/utils/InventoryParser.java`

**MODIFY:**
- `app/src/main/java/ru/neverlands/abclient/network/FishAjaxPhp.java` (main logic)
- `app/src/main/java/ru/neverlands/abclient/AppVars.java` (if needed for FishStopOverWeight)

## Code References from C#

**MainPhp.cs lines 555-575** (overweight check):
```csharp
if (AppVars.Profile.FishStopOverWeight && 
    html.IndexOf("<font color=#CC0000>Внимание! Возможен перегруз.", StringComparison.OrdinalIgnoreCase) != -1)
{
    AppVars.MainForm.UpdateFishOff();  // Stop fishing
    // Also check for: nope снарядов, нет приманки, нет умения
}
```

**MainPhpWear.cs lines 21-110** (rod wearing):
- Loop through inventory items
- For each item: check if name contains "удочка" or "спиннинг"
- If matches FishHandOne/FishHandTwo: extract WearLink
- Send WearLink to execute gear change
- If nothing found: stop auto-fish

**TInvUd.cs lines 190-245** (IsWear1/IsWear2):
- slist[0] или slist[1] - списоктекущей одежды в руках (сортировано по приоритету)
- Проверяет "Любая удочка" опцию или конкретное имя удочки

## Expected Outcome

After completion:
- ✅ Fishing continues when inventory fills (rod replacement)
- ✅ Fishing stops with notification when setting enabled (overweight stop)
- ✅ No server force-fights due to mass threshold
- ✅ Chat shows proper notifications
- ✅ Logs track all gear changes and decisions
- ✅ Stable 24/7 fishing without user intervention

---

## BUILD STATUS

**✅ BUILD SUCCESSFUL** - APK v1.1.4 compiled with all code changes  
**Date:** April 3, 2026  
**Compilation Time:** ~15 seconds  

### Files Modified/Created:
- ✅ `GearParser.java` - new class for parsing current gear
- ✅ `InventoryParser.java` - new class for parsing inventory items
- ✅ `FishAjaxPhp.java` - added overweight check + rod replacement logic
  - Added: `checkOverweightHtmlPattern()`
  - Added: `checkAndWearRodIfNeeded()`
  - Added: `executeWearLink()`
  - Modified: `processFishAct1()` - integrated new checks

### Ready for Testing:
The implementation is complete and compiled. Next phase requires:
1. Installation of APK v1.1.4 on test device
2. Manual testing with inventory fill scenario
3. Verification of rod replacement when needed
4. Verification of overweight stop with chat notification
5. Regression testing for normal fishing scenarios
