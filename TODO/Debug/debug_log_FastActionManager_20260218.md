# Debug Log: Портирование FormMainFast.cs -> FastActionManager.java
**Дата:** 2026-02-18

## Действия

### 1. Анализ исходного C# кода
- **Действие:** Прочитаны `FormMainFast.cs` (ABForms, ~375 строк) и `MainPhpFast.cs` (PostFilter, ~900 строк)
- **Результат:** Два файла составляют единую систему:
  - FormMainFast — управление (20+ методов fastAttack*, запуск потоков, ожидание боя)
  - MainPhpFast — парсинг HTML (switch по FastId, генерация форм с авто-submit)

### 2. Проверка существующей реализации
- **Действие:** Поиск Fast-переменных, менеджеров, интеграции
- **Результат:**
  - AppVars.Fast* переменные уже добавлены (шаг FormQuick)
  - QuickActionsBottomSheet уже создан (заглушки)
  - MainPhp.process() — нет вызова processMainPhp
  - HelperStrings.subString — метод существует

### 3. Создание TODO-файла
- **Действие:** `TODO/todo_ABClient/todo_ABForms/todo_FormMainFast.cs.md`
- **Результат:** Полный план с анализом обоих C# файлов

### 4. Создание FastActionManager.java
- **Действие:** Создан `manager/FastActionManager.java` (~350 строк)
- **Содержание:**
  - Часть 1 (FormMainFast): fastStart, fastCancel, stripItalic, 13 методов fastAttack*
  - Часть 2 (MainPhpFast): processMainPhp (диспетчер), mainPhpFastHit (универсальный для нападалок), mainPhpFastW28 (универсальный для свитков/порталов), mainPhpFastFog, mainPhpFastPotion
  - Утилиты: reloadMainFrame (через broadcast), indexOfIgnoreCase
- **Оптимизация:** В C# было 10+ отдельных методов для нападалок с копипастой, на Java объединено в один `mainPhpFastHit(html, validSubIds, description)` с параметрами

### 5. Интеграция с MainPhp.java
- **Действие:** Добавлена проверка `AppVars.FastNeed` в `MainPhp.process()`
- **Результат:** Если Fast-действие активно, вызывается `FastActionManager.processMainPhp(html)`

### 6. Обновление QuickActionsBottomSheet
- **Действие:** Заменены Toast-заглушки на вызовы `FastActionManager.fastAttack*(nick)`
- **Результат:** Кнопки теперь запускают реальную цепочку: fastAttack → fastStart → reloadMainFrame → MainPhp.process → processMainPhp → генерация формы → WebView submit

### 7. Сборка
- **Действие:** `./gradlew assembleDebug`
- **Результат:** BUILD SUCCESSFUL (2 warnings — deprecated, не связаны с нашим кодом)

## Итог
Полная цепочка быстрых атак портирована:
1. UI (QuickActionsBottomSheet) → 2. Управление (FastActionManager.fastAttack*) → 3. AppVars.FastNeed → 4. MainPhp.process() → 5. FastActionManager.processMainPhp() → 6. Генерация HTML формы → 7. WebView auto-submit

## Что НЕ портировано (отложено)
- [ ] FastAttackAsync — фоновое ожидание окончания боя цели (polling NeverApi.GetFlog). Требует полный порт NeverApi.
- [ ] FastAttackAutoAttack варианты (с DoPerenap)
- [ ] FastAttackIsland/Blaz/MomentCure/Restore/Primanka — специализированные действия
- [ ] FastAttackOpenNevid — обнаружение невидимок
- [ ] Эликсиры (mainPhpFastElixir) — отдельный парсер
- [ ] Тотем (mainPhpFastTotem) — отдельный парсер
