# План портирования FormMainChat.cs

Файл `FormMainChat.cs` — часть `FormMain` в C# версии. Содержит ключевую логику обработки чата: фильтрацию входящих сообщений (`ChatFilter`), обновление счетчиков опыта/трофеев, автоответы, звук, модификации HTML сообщения, а также вспомогательные методы записи сообщений в чат.

## 1. Функциональность в C#

### 1.1. Управление настройками чата
- `ChangeChatSize(int size)` → `AppVars.Profile.ChatHeight`
- `ChangeChatSpeed(int delay)` → `AppVars.Profile.ChatDelay`
- `ChangeChatMode(int mode)` → `AppVars.Profile.ChatMode`
- `ChatUpdated()` → `Chat.LastChanged = DateTime.Now`, `Chat.Critical = false`

### 1.2. ChatFilter(string message) — ключевая функция
Основной pipeline обработки сообщения:

1) **Боевой опыт (XP)**
- Извлекает число из строки:
  `Получено <font color=#CC0000>боевого</font> опыта: <b><font color=#CC0000>...`
- Парсит `long` и вызывает `UpdateXPInc(xp)` через `MainForm.BeginInvoke`.

2) **Трофеи/лут после боя**
- Ищет: `Результат обыска бота: <B>...</B>.`
- Извлекает время из `<font class=chattime>&nbsp;HH:MM:SS&nbsp;</font> ...`
- Парсит элементы списка через regex `«([^»]+)»`.
- Вызывает `UpdateThingInc(time, list)` через `MainForm.BeginInvoke`.

3) **Завершение боя**
- Триггер: `Системная информация. Поединок завершён.`
- Если заполнены `AppVars.LastBoiLog / LastBoiSostav / LastBoiTravm / LastBoiUron`:
  - Формирует расширенное сообщение с ссылкой на лог и уроном.
  - Удаляет блок магического опыта (`Получено <font color=#004BBB>магического...`).
  - Пишет тех‑лог: `UpdateTexLog("Бой против ... завершен (LogId)")`.
  - Очищает `AppVars.LastBoiLog/LastBoiSostav`.

4) **Приват/клан/парные сообщения и автоответ**
- Ищет сообщение адресованное своему нику (`">UserNick</SPAN>"`).
- Определяет `fromNick` из `<SPAN title="...">`, чистит `%`.
- Если это не свой ник:
  - Проигрывает звук `EventSounds.PlaySndMsg()`.
  - Определяет тип `clan:` или `pair:`.
  - Если `AppVars.Profile.DoAutoAnswer` — формирует ответ:
    - `%<fromNick> {AutoAnswer}`
    - добавляет `%clan%` или `%pair%` при необходимости.
  - Кладёт в очередь `Chat.AddAnswer(answer)`.

5) **Уровни и знаки (DoChatLevels)**
- Находит отправителя `sayNick` из ближайшего `</SPAN>`.
- Если `ChatUsersManager.Exists(sayNick)`:
  - Вставляет уровень и ссылку на pinfo после `</SPAN>`.
  - Вставляет знак (`sign/status`) перед `SPAN` (img + title).

6) **Маркеризация clan/pair**
- Если найдено `pair:` → `<SPAN title="%` заменяется на `<SPAN title="%%%`
- Если найдено `clan:` → `<SPAN title="%` заменяется на `<SPAN title="%%`

7) **[[[fid]]]**
- Заменяет `[[[fid]]]` на ссылку лога боя:
  `http://www.neverlands.ru/logs.fcg?fid={fid}`

8) **Финал**
- `Chat.AddStringToChat(message)` — пишет в лог/буфер.
- Возвращает модифицированный `message` обратно в JS.

### 1.3. Вспомогательные методы
- `WriteChatMsg/WriteChatMsgSafe/WriteChatTip` — формируют HTML‑строку с временем, маркером, цветом и кладут в чат.
- Учитывают `AppVars.Profile.ServDiff` (смещение серверного времени).

## 2. Текущее состояние в Android

- `WebAppInterface.chatFilter(String message)` — заглушка, просто логирует и возвращает исходное сообщение.
- `utils/Chat.addAnswer` — заглушка (нет очереди, нет отправки).
- `utils/Chat.addMessageToChat` — пишет в `chatMsgWebview` через `add_msg`.
- `AppVars` уже содержит `LastBoiLog/LastBoiSostav/LastBoiTravm/LastBoiTimer`.
- Нет полей в `UserConfig` для `ChatDelay/ChatMode/ChatHeight/ChatKeepLog/DoAutoAnswer/DoChatLevels`.
- Аналог `ChatUsersManager` → `manager.ChatUserList`, `model.ChatUser`.
- Тех‑лог есть через `utils.AppLogger.writeTexLog`.

## 3. Решение для портирования на Android

1) Перенести логику `ChatFilter` в Android:
   - Вынести в отдельный класс `ChatFilter` (например `utils.ChatFilter`) или реализовать внутри `WebAppInterface.chatFilter`.
   - Возвращать строку с модификациями, как в C#.

2) Обновления статистики:
   - Добавить аналоги `UpdateXPInc`, `UpdateThingInc`, `UpdateTexLog` в `MainActivity` или отдельный менеджер (например `StatsManager`).
   - Для тех‑лога использовать `AppLogger.writeTexLog`.

3) Автоответ:
   - Реализовать очередь в `Chat` (аналог `MyChat.Chat`).
   - Реализовать `AutoAnswerMachine` (или заглушку + TODO).
   - Организовать отправку сообщений из очереди в чат (таймер + вызов `chatSubmit`).

4) Уровни/знаки:
   - Использовать `ChatUserList` (exists/getUser) и вставлять уровень/знак в HTML, как в C#.

5) Логи чата:
   - Требование: сохранять в `files/logs/%timestamp%_chat.txt` (по условию пользователя).
   - Реализовать `ChatKeepLog` флаг в `UserConfig`.
   - Формат: можно писать чистый HTML или текст; но имя файла и путь должны совпадать с требованием.

## 4. План реализации

1. **Подготовка данных**
   - Добавить в `UserConfig` поля: `ChatDelay`, `ChatMode`, `ChatHeight`, `ChatKeepLog`, `DoAutoAnswer`, `DoChatLevels`.
   - Обеспечить сохранение/загрузку в профиле.

2. **Реализация `ChatFilter`**
   - Перенести блоки XP/лут/конец боя/автоответ/уровни/[[[fid]]] 1:1 по логике C#.
   - Использовать `AppVars.LastBoi*` и `ChatUserList`.

3. **Интеграция с UI/логами**
   - Добавить аналоги `UpdateXPInc`, `UpdateThingInc`, `UpdateTexLog`.
   - Связать с существующим UI (если нет — сделать минимальный лог в `AppLogger` + TODO).

4. **Очередь автоответов**
   - Реализовать в `utils.Chat` очередь + `getAnswer()` с задержкой (>=3 сек).
   - Добавить отправку сообщений по таймеру.

5. **Логирование чата**
   - Сохранять сообщения при `ChatKeepLog` в `files/logs/%timestamp%_chat.txt`.
   - Формировать файл при первом сообщении (заголовок/метаданные по необходимости).

6. **Проверка**
   - Логи: увидеть `ChatFilter` в логах при приходе системных и боевых сообщений.
   - Тесты: XP, лут, завершение боя, приват/клан автоответ, уровни/знаки.
