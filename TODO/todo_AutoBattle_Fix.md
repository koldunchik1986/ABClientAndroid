# Анализ: Авто-Бой (AutoBattle)

## Дата: 2026-02-26

## Проблема

При отключении кнопки "Авто-Бой" во время боя, верхний фрейм становится белым. При повторном включении авто-боя, фрейм остаётся белым и авто-бой не работает, потому что клиент не может определить, что мы находимся в бою.

## Анализ исходного кода ПК версии (C#)

### Файл: ABClient/ABForms/FormMainAutoBoi.cs

Основные методы:
- `AutoSelect()` - выбирает комбинацию ударов/блоков/абилок без отправки на сервер
- `AutoTurn()` - делает автоматический ход (выбирает и отправляет)
- `AutoBoi()` - включает авто-бой и перезагружает страницу
- `ChangeButtonAutoboiState()` - переключает состояние автобоя
- `ChangeAutoboiState(AutoboiState state)` - изменяет состояние и обновляет текст кнопки

### Поведение в ПК версии:

1. При нажатии кнопки автобоя вызывается `ChangeButtonAutoboiState()`
2. Метод переключает состояние `AppVars.Autoboi` между `AutoboiOn` и `AutoboiOff`
3. Обновляется `AppVars.Profile.LezDoAutoboi`
4. Вызывается `ReloadMainPhpInvoke()` для перезагрузки страницы

## Реализация на Android

### Внесённые изменения:

#### 1. FightViewModel.java (строка 73-75)
Добавлен метод для синхронизации состояния:
```java
public void setAutoBattleActive(boolean active) {
    _isAutoBattleActive.setValue(active);
}
```

#### 2. WebAppInterface.java (строки 288-320)
Обновлён метод `AutoBoi()` для синхронизации состояния FightViewModel:
```java
@JavascriptInterface
public void AutoBoi() {
    if (AppVars.Autoboi == AutoboiState.AutoboiOn) {
        AppVars.Autoboi = AutoboiState.AutoboiOff;
        if (AppVars.Profile != null) {
            AppVars.Profile.LezDoAutoboi = false;
        }
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().getFightViewModel().setAutoBattleActive(false);
        }
    } else {
        AppVars.Autoboi = AutoboiState.AutoboiOn;
        if (AppVars.Profile != null) {
            AppVars.Profile.LezDoAutoboi = true;
        }
        if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
            AppVars.mainActivity.get().getFightViewModel().setAutoBattleActive(true);
        }
    }
}
```

#### 3. AutoFunctionsManager.java (строки 47-65)
Обновлён метод `setAutoFightEnabled()`:
```java
public void setAutoFightEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_PREFIX + "auto_fight", enabled).apply();
    AppVars.Autoboi = enabled ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff;
    
    if (AppVars.Profile != null) {
        AppVars.Profile.LezDoAutoboi = enabled;
    }
    
    if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
        AppVars.mainActivity.get().getFightViewModel().setAutoBattleActive(enabled);
        AppVars.mainActivity.get().runOnUiThread(() -> {
            AppVars.mainActivity.get().binding.appBarMain.contentMain.webView.reload();
        });
    }
}
```

#### 4. MainPhp.java (строка ~598)
Изменена логика проверки автобоя:
```java
// Было: if (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi)
// Стало: boolean isAutoboiEnabled = (AppVars.Autoboi == AutoboiState.AutoboiOn);
```

#### 5. FightJs.java (строки 113-127)
Обновлена функция JavaScript AutoBoi():
```javascript
function AutoBoi() {
    try {
        AutoSelect();
    } catch(e) { console.log('AutoSelect error: ' + e); }
    try {
        AndroidBridge.AutoBoi();
    } catch(e) { console.log('AutoBoi error: ' + e); }
    try {
        AndroidBridge.reloadPage();
    } catch(e) { console.log('reloadPage error: ' + e); location.reload(); }
}
```

#### 6. WebAppInterface.java (новый метод)
Добавлен метод для перезагрузки страницы:
```java
@JavascriptInterface
public void reloadPage() {
    if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
        AppVars.mainActivity.get().runOnUiThread(() -> {
            AppVars.mainActivity.get().binding.appBarMain.contentMain.webView.reload();
        });
    }
}
```

## Статус

- [x] Авто-бой работает при выключении и включении
- [x] Белый фрейм при отключенном авто-бое (ИСПРАВЛЕНО)

## Исправление

### Проблема
В `MainPhp.java:677` при отключённом авто-бое возвращался `AppVars.ContentMainPhp`, который сохранялся **до** загрузки текущей страницы боя (в строке 42). Это приводило к белому фрейму, так как показывался контент с предыдущей страницы.

### Решение
При отключённом авто-бое возвращать **текущий** `html` (параметр метода), а не старый `ContentMainPhp`:

```java
// Было:
return AppVars.ContentMainPhp != null ? AppVars.ContentMainPhp : html;

// Стало:
return html;
```

Это позволяет показывать боевую страницу без авто-ходов корректно.
