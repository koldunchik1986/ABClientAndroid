# Инструкция по доработке QuickButtons (Быстрые кнопки)

## Статус анализа

Данная инструкция основана на анализе:
- `TODO\todo_QuickButtons.java.md` - документация
- `TODO\todo_QuickButtons.md` - план
- `app\src\main\java\ru\neverlands\abclient\ui\QuickButtonsPanel.java` - реализация UI
- `app\src\main\java\ru\neverlands\abclient\manager\QuickButtonsManager.java` - менеджер
- `app\src\main\java\ru\neverlands\abclient\model\QuickActionType.java` - enum
- `app\src\main\java\ru\neverlands\abclient\model\QuickButton.java` - модель
- `app\src\main\java\ru\neverlands\abclient\adapter\FunctionListAdapter.java` - адаптер
- `app\src\main\java\ru\neverlands\abclient\manager\AutoFunctionsManager.java` - менеджер автофункций

## Что реализовано

### Java классы
| Файл | Статус | Описание |
|------|--------|----------|
| `QuickButtonsPanel.java` | ✅ Готов | UI панели кнопок |
| `QuickButtonsManager.java` | ✅ Готов | Управление кнопками (SharedPreferences) |
| `QuickActionType.java` | ✅ Готов | Enum типов действий |
| `QuickButton.java` | ✅ Готов | Модель кнопки |
| `FunctionListAdapter.java` | ✅ Готов | Адаптер списка функций |
| `AutoFunctionsManager.java` | ✅ Готов | Менеджер автофункций (toggle вкл/выкл) |

### Layout файлы
| Файл | Статус |
|------|--------|
| `quick_buttons_bar.xml` | ✅ Готов (10 кнопок) |
| `quick_buttons_bar_bottom.xml` | ✅ Готов (10 кнопок) |
| `dialog_select_function.xml` | ✅ Готов |
| `item_function.xml` | ✅ Готов |
| `dialog_input_nick.xml` | ✅ Готов (для PINFO) |

### Иконки
| Файл | Статус |
|------|--------|
| `ic_info.xml` | ✅ Готов (иконка для PINFO) |
| `ic_auto_fight.xml` | ✅ Готов (автобой) |
| `ic_auto_recall.xml` | ✅ Готов (авторыбалка) |
| `ic_auto_hunt.xml` | ✅ Готов (автоохота) |
| `ic_auto_attack.xml` | ✅ Готов (автонападение) |
| `ic_auto_invisible.xml` | ✅ Готов (автоневид) |
| `ic_location.xml` | ✅ Готов (слежение) |
| `ic_auto_detect.xml` | ✅ Готов (автообнаружение) |
| `ic_auto_summon.xml` | ✅ Готов (автопризыв) |
| `ic_auto_heal.xml` | ✅ Готов (автолечение) |

### Интеграция
- ✅ Панели добавлены в `content_main.xml`
- ✅ Инициализация в `MainActivity.java`
- ✅ Сохранение в SharedPreferences работает
- ✅ Загрузка иконок через Glide
- ✅ OPEN_PINFO - диалог ввода ника → открытие вкладки через TabManager
- ✅ Автофункции - переключение вкл/выкл через AutoFunctionsManager

---

## Что НЕ реализовано

### 1. Иконки для автофункций (вкл/выкл)

Требуется создать иконки для визуального отображения состояния автофункций:
- `ic_auto_fight_on.xml` / `ic_auto_fight_off.xml`
- `ic_auto_recall_on.xml` / `ic_auto_recall_off.xml`
- и т.д.

### 2. Реальное выполнение автофункций

Пока реализовано только переключение состояния (вкл/выкл). Требуется реализовать:
- Логику автобоя (используя LezFight)
- Логику авторыбалки
- Логику автоохота
- и т.д.

---

## План доработки

### Этап 1: Иконки для автофункций

1. Создать иконки в `res/drawable/`:
   - Для каждой автофункции нужны 2 иконки (вкл/выкл)
   - Пока можно использовать одну иконку с разной прозрачностью

2. Обновить `QuickButtonsPanel.java`:
   - Метод `getIconForAction(type, isEnabled)` для возврата разных иконок

### Этап 2: Реализация логики автофункций

1. Для каждой автофункции реализовать реальную логику:
   - `AutoFunctionsManager.toggleAutoFight()` → запуск/остановка автобоя
   - Подключить `LezFight` для логики автобоя

---

## QuickActionType - текущий статус

```
Основные действия:
- AUTO_FIGHT          ("Автобой") ✅ Переключение вкл/выкл
- QUICK_ACTIONS       ("Быстрые действия") ✅ Работает
- AUTO_RECALL         ("Авторыбалка") ✅ Переключение вкл/выкл
- AUTO_HUNT           ("Автоохота") ✅ Переключение вкл/выкл
- AUTO_ATTACK         ("Автонападение") ✅ Переключение вкл/выкл
- AUTO_INVISIBLE      ("АвтоНевид") ✅ Переключение вкл/выкл
- LOCATION_TRACKING   ("Слежение за локацией") ✅ Переключение вкл/выкл
- AUTO_DETECT         ("АвтоОбнаружение") ✅ Переключение вкл/выкл
- AUTO_SUMMON         ("АвтоПризыв") ✅ Переключение вкл/выкл
- AUTO_HEAL           ("АвтоЛечение") ✅ Переключение вкл/выкл

Дополнительные:
- OPEN_CONTACTS       ("Открыть контакты") ✅ Работает
- OPEN_PINFO          ("Открыть PINFO") ✅ Работает
- OPEN_LOGS           ("Открыть Логи") ✅ Работает
- REFRESH_CONTACTS   ("Обновить контакты") ✅ Работает

Быстрые действия на себя:
- QUICK_SELF_RASS     ("Рассеять невид") ✅ Работает
- QUICK_OPEN_NEVID    ("Обнаружение") ✅ Работает
- QUICK_TELEPORT      ("Телепорт") ✅ Работает
- QUICK_ISLAND        ("Остров") ✅ Работает
- QUICK_TOTEM         ("Тотем") ✅ Работает
- QUICK_ELIXIR_BLAZ   ("Эликсир Блаженства") ✅ Работает
- QUICK_ELIXIR_CURE   ("Эликсир Исцеления") ✅ Работает
- QUICK_ELIXIR_RESTORE("Эликсир Восстановления") ✅ Работает
```

---

## Следующие шаги

1. ⏳ Реализовать логику выполнения автофункций

