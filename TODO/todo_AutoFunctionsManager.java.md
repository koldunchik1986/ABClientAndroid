# Инструкция по AutoFunctionsManager

## Назначение файла

Класс `AutoFunctionsManager` предназначен для управления автоматическими функциями (автобой, авторыбалка, автоохота и т.д.), которые вызываются из QuickButtonsPanel.

## Анализ существующего кода

### QuickButtonsPanel - что уже есть

В `QuickButtonsPanel.java` уже есть:
- Enum `QuickActionType` с типами автофункций
- Метод `executeAction(int position)` обрабатывает нажатия
- Для автофункций показывается только Toast (строки 270-301)

### Текущая реализация (только Toast):
```java
case AUTO_FIGHT:
    Toast.makeText(context, "Автобой", Toast.LENGTH_SHORT).show();
    break;
case AUTO_RECALL:
    Toast.makeText(context, "Авторыбалка", Toast.LENGTH_SHORT).show();
    break;
// ... и т.д.
```

## Что нужно реализовать

### AutoFunctionsManager - структура

```java
package ru.neverlands.abclient.manager;

public class AutoFunctionsManager {
    private static final String TAG = "AutoFunctionsManager";
    private static AutoFunctionsManager instance;
    
    // Состояния автофункций
    private boolean autoFightEnabled = false;
    private boolean autoRecallEnabled = false;
    private boolean autoHuntEnabled = false;
    private boolean autoAttackEnabled = false;
    private boolean autoInvisibleEnabled = false;
    private boolean locationTrackingEnabled = false;
    private boolean autoDetectEnabled = false;
    private boolean autoSummonEnabled = false;
    private boolean autoHealEnabled = false;
    
    // Методы управления
    public static synchronized AutoFunctionsManager getInstance(Context context)
    public void toggleAutoFight()
    public void startAutoFight()
    public void stopAutoFight()
    public boolean isAutoFightEnabled()
    
    // ... аналогично для других функций
}
```

### Методы FastActionManager которые можно использовать

Из `FastActionManager.java`:
- `fastAttack(String nick)` - атака по нику
- `fastAttackAsync(String weapon, String nick)` - асинхронная атака

### Интеграция с QuickButtonsPanel

В `QuickButtonsPanel.executeAction()` заменить:
```java
// БЫЛО:
case AUTO_FIGHT:
    Toast.makeText(context, "Автобой", Toast.LENGTH_SHORT).show();
    break;

// СТАТЬ:
case AUTO_FIGHT:
    AutoFunctionsManager.getInstance(context).toggleAutoFight();
    break;
```

## План реализации

1. [x] Создать класс `AutoFunctionsManager.java`
2. [x] Реализовать методы toggle/start/stop для каждой автофункции
3. [x] Обновить `QuickButtonsPanel.java` - добавить вызовы
4. [ ] Добавить иконки для автофункций (два состояния: вкл/выкл)
5. [ ] Реализовать визуальный индикатор состояния (вкл/выкл)

## Иконки для автофункций

Каждая автофункция должна иметь две иконки:
- **Выключено** (по умолчанию): серый/контурный вариант
- **Включено**: цветной/залитый вариант

### Требуемые иконки

| Функция | Иконка выкл | Иконка вкл |
|---------|-------------|------------|
| AUTO_FIGHT | ic_auto_fight_off.xml | ic_auto_fight_on.xml |
| AUTO_RECALL | ic_auto_recall_off.xml | ic_auto_recall_on.xml |
| AUTO_HUNT | ic_auto_hunt_off.xml | ic_auto_hunt_on.xml |
| AUTO_ATTACK | ic_auto_attack_off.xml | ic_auto_attack_on.xml |
| AUTO_INVISIBLE | ic_auto_invisible_off.xml | ic_auto_invisible_on.xml |
| LOCATION_TRACKING | ic_location_tracking_off.xml | ic_location_tracking_on.xml |
| AUTO_DETECT | ic_auto_detect_off.xml | ic_auto_detect_on.xml |
| AUTO_SUMMON | ic_auto_summon_off.xml | ic_auto_summon_on.xml |
| AUTO_HEAL | ic_auto_heal_off.xml | ic_auto_heal_on.xml |

### Интеграция иконок в QuickButtonsPanel

```java
// В методе getIconForAction() добавить учет состояния:
private int getIconForAction(QuickActionType type, boolean isEnabled) {
    switch (type) {
        case AUTO_FIGHT:
            return isEnabled ? R.drawable.ic_auto_fight_on : R.drawable.ic_auto_fight_off;
        // ...
    }
}

// В методе updateButtonAppearance() передавать состояние:
boolean isEnabled = AutoFunctionsManager.getInstance(context).isAutoFightEnabled();
updateButtonAppearance(position, button, isEnabled);
```

## Особенности реализации

### Автобой (AUTO_FIGHT)
Требует взаимодействия с WebView для отправки команд на сервер.
См. `ABClient\PostFilter\Fight.cs` для понимания логики.

### Авторыбалка (AUTO_RECALL)
См. `ABClient\PostFilter\Recall.cs`

### Автоохота (AUTO_HUNT)
См. `ABClient\PostFilter\Hunt.cs`

### Автонападение (AUTO_ATTACK)
См. `ABClient\PostFilter\AutoAttack.cs`

### АвтоНевид (AUTO_INVISIBLE)
См. `ABClient\PostFilter\Invisible.cs`

### Слежение за локацией (LOCATION_TRACKING)
См. `ABClient\PostFilter\LocationTracking.cs`

### АвтоОбнаружение (AUTO_DETECT)
См. `ABClient\PostFilter\Detect.cs`

### АвтоПризыв (AUTO_SUMMON)
См. `ABClient\PostFilter\Summon.cs`

### АвтоЛечение (AUTO_HEAL)
См. `ABClient\PostFilter\Heal.cs`

## Зависимости

- `android.content.Context`
- `android.webkit.WebView` - для отправки команд
- `ru.neverlands.abclient.manager.FastActionManager`
- `ru.neverlands.abclient.model.QuickActionType`
- `ru.neverlands.abclient.model.AutoboiState` - состояния автобоя
- `ru.neverlands.abclient.lez.LezFight` - логика автобоя

## Существующая инфраструктура

### AutoboiState (уже реализовано в Android)
```java
public enum AutoboiState {
    AutoboiOff,    // Выключено
    AutoboiOn,     // Автобой включен
    Restoring,     // Восстановление
    Timeout,       // Ожидание таймаута
    Guamod         // Распознавание капчи
}
```

### AppVars содержит
```java
public static AutoboiState Autoboi = AutoboiState.AutoboiOff;
```

### LezFight (уже реализовано в Android)
Класс для логики ведения боя.

### Таймер в MainActivity (уже есть)
Запускается в `startTimer()`, выполняется каждую секунду. Можно использовать для проверки условий автофункций.
