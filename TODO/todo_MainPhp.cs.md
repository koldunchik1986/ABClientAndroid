# План портирования MainPhp.cs

Этот документ описывает подробный план портирования `MainPhp.cs` — самого сложного и критически важного компонента `PostFilter`. Этот файл является "мозгом" клиента, управляя почти всей автоматизацией и логикой взаимодействия с основным игровым фреймом.

## 1. Архитектурные проблемы и решения

Прямое портирование "один в один" невозможно по следующим причинам:

*   **Сильная связь с UI**: Код постоянно вызывает `AppVars.MainForm.BeginInvoke` для обновления UI (WinForms). В Android это нужно заменить на современный подход, например, `ViewModel` + `LiveData` или `EventBus`, чтобы отделить логику от представления.
*   **Бесконтрольные `goto`**: Логика построена на многочисленных `goto end;`, что делает код трудным для чтения и поддержки. В Java это нужно будет рефакторить в четкие `if-else` блоки и возвраты из методов.
*   **Монолитность**: Вся логика свалена в один гигантский метод `MainPhp`. Это нужно разбить на множество мелких, переиспользуемых методов, каждый из которых отвечает за свою задачу.

**Предлагаемая архитектура в Java:**

Создать класс `MainPhpProcessor`, который будет содержать всю логику. Основной метод `process(address, html)` будет вызывать другие методы в строгом порядке, реализуя конвейер обработки.

## 2. Поэтапный план реализации

### Фаза 1: Создание базовой "заглушки" (текущий этап)

Цель — обеспечить работоспособность приложения, отложив сложную логику.

*   **`MainPhp.java`**: Создать класс.
*   **`public static byte[] process(String address, byte[] array)`**: Основной метод.
    *   Конвертирует `array` в `html` строку.
    *   Вызывает `filterGetLocation(address)` для парсинга координат.
    *   Обновляет `AppVars.IdleTimer` и `AppVars.LastMainPhp`.
    *   Сохраняет `html` в `AppVars.ContentMainPhp`.
    *   Возвращает исходный `html` в виде `byte[]`.
*   **`private static void filterGetLocation(String url)`**: Портировать логику извлечения `gx` и `gy` из URL с помощью регулярных выражений.

### Фаза 2: Парсинг состояния игрока

Цель — извлечь всю возможную информацию о состоянии персонажа со страницы `main.php` и сохранить ее в `AppVars`.

*   **`private static void parsePlayerState(String html)`**: Новый метод, который будет вызывать все парсеры ниже.
*   **`private static void parseHpAndMp(String html)`**: Найти и извлечь текущие/максимальные HP и MP (ищет вызов `ins_HP(...)`).
*   **`private static void parsePoisonAndWounds(String html)`**: Найти и извлечь информацию о наличии отравлений и травм.
*   **`private static void parseFatigue(String html)`**: Найти и извлечь текущее значение усталости (ищет `Усталость: ...`).
*   **`private static void parseSkills(String html)`**: Найти и извлечь уровни умений (Рыбалка, Охота).
*   **`private static void parseComplects(String html)`**: Найти и извлечь список доступных комплектов одежды (ищет `compl_view(...)`).

### Фаза 3: Обработка системных сообщений

Цель — централизованно обрабатывать все информационные и ошибочные сообщения от сервера.

*   **`private static void handleSystemMessages(String html)`**: Новый метод, который будет проверять наличие различных сообщений.
    *   `"Сеанс работы прерван"` -> Уведомить UI о необходимости перезахода.
    *   `"Ошибка при использовании..."` -> Уведомить UI, отменить `FastAction`.
    *   `"Сделка удачно завершена"` -> Уведомить UI, сбросить триггеры торговли.
    *   `"Результат воровства: ..."` -> Извлечь и показать в чате.
    *   И так далее для всех `html.IndexOf(...)` проверок из оригинала.

### Фаза 4: Реализация автоматизации ("Бота")

Это самая большая фаза. Каждый пункт — отдельная крупная задача.

*   **`private static String handleAutoCure(String html, String address)`**: Логика авто-лечения травм и отравлений. Должна проверять флаг `AppVars.Profile.DoAutoCure`, наличие травм, искать нужные зелья/свитки в инвентаре и генерировать редирект на их использование.
*   **`private static String handleAutoDrink(String html, String address)`**: Логика питья эликсиров от усталости. Проверяет флаг `DoAutoDrinkBlaz` и уровень усталости.
*   **`private static String handleAutoFishing(String html, String address)`**: Логика авто-рыбалки. Проверяет флаг `FishAuto`, усталость, наличие удочки и наживки, ищет на странице возможность закинуть удочку.
*   **`private static String handleAutoSkinning(String html, String address)`**: Логика авто-разделки. Проверяет флаг `SkinAuto`, наличие трупа, наличие и экипировку ножа.
*   **`private static String handleFastActions(String html, String address)`**: Логика быстрых действий (нападения, использование предметов на цель). Проверяет флаг `AppVars.FastNeed`, определяет тип действия по `AppVars.FastId` и ищет нужный предмет/свиток.
*   **`private static String handleAutoNavigation(String html, String address)`**: Логика авто-перемещения. Проверяет флаг `AppVars.AutoMoving` и вызывает `MapAjax` или `TeleportAjax` для перехода к следующей точке.
*   **`private static String handleAutoWear(String html, String address)`**: Логика авто-одевания комплектов. Проверяет `AppVars.WearComplect` и ищет нужный комплект для одевания.

### Фаза 5: Вспомогательные функции и рефакторинг

*   **`private static String buildRedirect(String reason, String url)`**: Стандартизированная функция для генерации HTML-редиректа. Заменит все `goto end;` после создания редиректа.
*   **`private static boolean isInventoryOpen(String html)`**: Проверяет, открыт ли инвентарь.
*   **`private static boolean isCharacterScreenOpen(String html)`**: Проверяет, открыт ли экран персонажа.
*   **`private static String findItemAndBuildRedirect(String html, String itemIdentifier)`**: Универсальная функция для поиска предмета в инвентаре и генерации редиректа на его использование.
*   **Рефакторинг UI-взаимодействия**: Заменить все вызовы `BeginInvoke` на единый механизм (например, `EventBus.getDefault().post(new ShowChatMessageEvent("..."));`).

После завершения всех фаз, основной метод `process` будет выглядеть как чистый и понятный конвейер, вызывающий `parsePlayerState`, `handleSystemMessages`, `handleAutoCure` и т.д. в правильном порядке.