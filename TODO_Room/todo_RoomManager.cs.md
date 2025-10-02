# План портирования RoomManager.cs

Файл `RoomManager.cs` реализует ключевой функционал отслеживания игроков в текущей игровой локации (комнате). Он опрашивает страницу `ch.php?lo=1`, парсит список игроков (`ChatListU`), обновляет UI (выпадающие списки ПВ и травмированных), уведомляет пользователя о входе/выходе других персонажей и управляет автоматическими действиями (атака врагов из ЧС, использование свитка обнаружения).

## Функциональность в C#

### Назначение
- **Фоновый мониторинг**: Каждые 100 мс опрашивает `ch.php?lo=1` через `HttpWebRequest`.
- **Парсинг игроков**: Извлекает из `ChatListU` ник, уровень, клан, статус, травмы, невидимость.
- **Обновление UI**: Формирует списки `pvlist` и `trlist` для отображения в `dropdownPv` и `dropdownTravm`.
- **Уведомления**: При изменении состава комнаты (вход/выход игроков) отправляет сообщения в чат и проигрывает звук.
- **Автоматические действия**:
  - Если в комнате есть невидимки и включена настройка `AutoOpenNevid` — использует свиток обнаружения.
  - Если в комнате есть игрок из ЧС (`ContactsManager.GetClassIdOfContact == 1`) — атакует его.

### Ключевые методы
- `StartTracing()`: Запускает фоновый поток `RoomAsync`.
- `StopTracing()`: Останавливает фоновый поток.
- `RoomAsync(object stateInfo)`: Основной цикл опроса.
- `Process(string html)`: Основной метод обработки HTML.
- `FilterProcRoom(string html)`: Парсит `ChatListU` и формирует данные для UI.
- `FilterGetWalkers(string html)`: Сравнивает списки игроков и формирует уведомления.
- `HtmlChar(string schar)`: Генерирует HTML-код для отображения одного игрока.

### Глобальные переменные (в `AppVars`)
- `MyLocation`: `List<string>` — список ников игроков в комнате.
- `MyCharsOld`: `Dictionary<string, string>` — предыдущий список игроков для сравнения.
- `MyNevids`: `int` — количество невидимок.
- `MyNevidsOld`: `int` — предыдущее количество невидимок.
- `MyLocOld`: `string` — предыдущее название локации.
- `MyCoordOld`: `string` — предыдущие координаты.
- `MyWalkers1/2`: `string` — сформированные сообщения о входе/выходе.
- `DoShowWalkers`: `bool` — флаг включения уведомлений.
- `AutoOpenNevid`: `bool` — флаг автоматического использования свитка.
- `FastNeed`: `bool` — флаг блокировки автоматических действий.
- `LocationName`: `string` — название текущей локации.

### UI-методы (в `FormMain`)
- `UpdateRoom(ToolStripItem[] tsmi, string trtext, ToolStripItem[] tstr)`: Обновляет выпадающие списки ПВ и травм.
- `WriteChatMsgSafe(string message)`: Безопасно отправляет сообщение в чат.
- `FastAttackOpenNevid()`: Использует свиток обнаружения.
- `BeginInvoke(...)`: Обновляет UI из фонового потока.

### Вспомогательные классы
- `ContactsManager`: Проверяет принадлежность игрока к ЧС (`GetClassIdOfContact`) и получает ID предмета для атаки (`GetToolIdOfContact`).
- `EventSounds`: Проигрывает звук (`PlayAlarm()`).
- `Dice`: Генерирует случайное число (`Make(int max)`).

## Решение для портирования на Android

Для полного клонирования функционала на Android необходимо:
1.  Реализовать фоновый опрос с использованием `ScheduledExecutorService`.
2.  Перенести логику парсинга и сравнения списков.
3.  Обновлять UI через `runOnUiThread` или `Handler`.
4.  Интегрироваться с существующей системой `WebView` и `AndroidBridge`.

Ключевые классы и методы:
- `RoomManager.java`: Основной класс, аналог `RoomManager.cs`.
- `MainActivity.java`: Для обновления UI и запуска фонового опроса.
- `AppVars`: Для хранения глобального состояния.
- `ContactsManager`: Для проверки ЧС.

## План реализации

### Шаг 1: Реализация фонового опроса
- [x] Создать в `RoomManager.java` статические поля:
  ```java
  private static ScheduledExecutorService scheduler;
  private static volatile boolean doStop = false;
  private static String oldRoom = "";
- [ ] Реализовать методы startTracing(Context context) и stopTracing().
- [ ] Внутри задачи опроса использовать HttpURLConnection для получения ch.php?lo=1.
- [ ] Сравнивать newRoom с oldRoom и вызывать process(html) при изменении.

## Шаг 2: Полный парсинг ChatListU
- [ ] В методе filterProcRoom(String html) реализовать полный парсинг всех полей игрока (pars[0]..pars[8]):
      Обработка ПВ (pars[3].startsWith("pv")).
      Обработка травм (pars[6] != "0"), включая парсинг времени и типа травмы.
      Формирование enemyAttack с учётом ContactsManager.getClassIdOfContact().
      Обновление глобального списка AppVars.myLocation.
## Шаг 3: Реализация FilterGetWalkers
- [ ] Ввести в AppVars необходимые поля: myCharsOld, myNevidsOld, myLocOld, myCoordOld.
- [ ] Реализовать сравнение списков и формирование сообщений myWalkers1/myWalkers2.
## Шаг 4: Обновление UI
- [ ] В MainActivity создать метод updateRoom(List<MenuItem> pvList, String travmText, List<MenuItem> travmList).
- [ ] Вызывать этот метод из RoomManager через AppVars.mainActivity.get().runOnUiThread().
## Шаг 5: Интеграция автоматических действий
- [ ] Реализовать логику автоматического использования свитка обнаружения (AppVars.autoOpenNevid).
- [ ] Реализовать автоматическую атаку врагов из ЧС через AndroidBridge.fastAttack...().
## Шаг 6: Модификация HTML и генерация списка игроков
- [ ] Убедиться, что в process() добавляется выпадающий список навигации.
- [ ] Реализовать подсветку фона при наличии невидимок.
- [ ] Добавить кнопку "Свиток Обнаружения" с вызовом AndroidBridge.fastAttackOpenNevid().
- [!] Ключевая задача: Сгенерировать и вставить в HTML JavaScript-код, который вызовет chatlist_build() для отображения списка игроков. Это должно быть сделано после парсинга ChatListU.
## Шаг 7: Инициализация и очистка
- [ ] Вызывать RoomManager.startTracing(this) в MainActivity.onCreate().
- [ ] Вызывать RoomManager.stopTracing() в MainActivity.onDestroy().