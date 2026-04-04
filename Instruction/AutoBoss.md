# Анализ логов Auto-Босса (04.04.2026)

## Что анализировал
- `Logs/Блудя/20260404_chat.html`
- `Logs/Critical/20260404_boss_auto.log`
- `Logs/Critical/20260404_auto_boss.log`
- `Logs/Logcat/20260404_19_10_logcat.txt`
- `Logs/Logcat/20260404_19_30_logcat.txt`

## Краткий итог
- Сценарий `Авто-Боссы` отработал два раза успешно по цепочке: детект события → поиск цели → вход в бой → попытка возврата.
- Сообщение `%clan% ... возможно на клетках ...` **фактически отправлялось**, но в чат не отобразилось.
- Сообщения `я нападаю на перса ...` в клан-чат проходили стабильно.
- Во втором кейсе сценарий завершился `return_timeout` (не `return_completed`).

## Пошаговый разбор сценариев

## Сценарий 1 (19:15:21, Королева Змей → -ТочКа-)
1. Серверное событие попало в чат (`NeverLands.Ru`, `class=massm`) и было распознано в `BossAuto.onIncomingChatMessage(...)`.
2. Парсинг цели:
   - `parseBossEvent(...)` → `bossName=Королева Змей`, `targetNick=-ТочКа-`.
3. Запуск оркестрации:
   - `handleBossEvent(...)`:
   - anti-dup: `lastEventKey`, `lastEventAtMs`, `EVENT_DEDUP_WINDOW_MS`;
   - фильтры: `isAutoBossBdModeEnabled()`, `isAutoBossTrackCurrentWarsEnabled()`;
   - фиксация боя: `initialFightFid`, `initialFightLink`;
   - запуск поиска: `owner.startSettingsCompassTargetSearch(normalizedTarget, "auto_boss_event")`.
4. Клан-уведомление о возможных клетках:
   - `sendClanBossEventMessageIfNeeded(...)` вызван;
   - в `20260404_boss_auto.log` есть:
     - `[BOSS_CLAN_MSG_PAYLOAD] len=156`;
     - `[BOSS_CLAN_MSG_SCHEDULED] ... chatReady=true`;
     - `[BOSS_CLAN_MSG_SENT]`.
5. Поиск и вход в бой:
   - `onTargetFoundInRoom("room_list")` → `sendProtectionScroll()` → `FastActionManager.fastAttackZas(...)`;
   - локальное сообщение: `Используем «Свиток Защиты»...`;
   - дальше `WAIT_FIGHT_START` → `FIGHT_IN_PROGRESS`.
6. Завершение:
   - `startReturnOrRestore("fight_pulse_idle")`;
   - итог `return_completed`.

## Сценарий 2 (19:33:03, Выползень → Vito_Scaletta)
1. Событие распознано тем же контуром.
2. Клан-уведомление о возможных клетках:
   - есть `[BOSS_CLAN_MSG_TRIM]`, `[BOSS_CLAN_MSG_PAYLOAD]`, `[BOSS_CLAN_MSG_SENT]`.
3. Цель найдена, защита отправлена, бой начался.
4. Выход из боя:
   - возврат на исходную клетку стартовал (`return to origin started`);
   - сценарий завершился `return_timeout` (таймаут возврата), не `return_completed`.

## Почему не было видно `%clan% ... возможно на клетках ...`
- По логам отправка была (`Chat.sendMessageToServer`, `clanPrefix=true`, и `[BOSS_CLAN_MSG_SENT]`).
- В payload использовался HTML-якорь внутри текста (`в <a href=...>бою</a>`).
- Для `%clan%` это ненадежно: сервер/чат может фильтровать или искажать HTML в пользовательском тексте.
- Поэтому итог: сообщение технически отправлено, но не дошло в ожидаемом формате в клан-ленту.

## Что исправил в коде

### 1) Надежный формат clan-message без HTML в payload
- Файл: `app/src/main/java/ru/neverlands/abclient/manager/BossAuto.java`
- Изменения:
  - добавлен `FIGHT_FID_IN_LINK_PATTERN`;
  - добавлен `normalizeClanFightPartForSend(String fightPart)`;
  - в `buildClanBossEventMessage(...)` теперь перед сборкой текста вызывается нормализация `fightPart`.
- Новая логика:
  - если в `fightPart` был `<a href=...fid=...>`, он переводится в `в бою [[[fid]]]`;
  - это совместимо с уже существующей заменой `[[[fid]]] -> "лог боя"` в `ChatFilter`.

### 2) Сообщение «я нападаю...» теперь с номером клетки
- Файл: `app/src/main/java/ru/neverlands/abclient/manager/UnderAttackManager.java`
- Изменения:
  - точка вызова переключена с `buildLocationSuffix()` на `buildLocationSuffixByRegNum()`;
  - добавлен `buildLocationSuffixByRegNum()`:
    - приоритет источников: `AppVars.Profile.MapLocation` → `AppVars.AutoMovingDestinaton` → `AppVars.myLocOld`;
    - формат: `", клетка № <regNum>"`.

### 3) Server-timestamp для системных сообщений NeverLands.Ru
- Файл: `app/src/main/java/ru/neverlands/abclient/utils/ChatFilter.java`
- Изменения:
  - добавлены `shouldAddServerTimestampPrefix(...)` и `buildServerChatTimeHtml()`;
  - в `filter(...)` добавлен префикс `<font class=chattime>...</font>` для входящих системных серверных сообщений (`class=massm` + `NeverLands.Ru`), если timestamp отсутствует.

## Ошибки/аномалии, замеченные в логах
- `return_timeout` во втором сценарии (`19:36:26`):
  - цепочка боя прошла, но возврат не успел завершиться в `RETURN_TIMEOUT_MS`.
  - Нужно отдельно усилить трассировку возврата (текущая/целевая клетка + состояние `AppVars.AutoMoving` на каждом тике `RETURNING_TO_ORIGIN`).
- Повторяющиеся `chat-poll degraded code=546` и `NeverApi HTTP 536`:
  - сценарии при этом не ломались, но это фактор нестабильности сети/прокси.

## Что проверить в следующем прогоне
1. С включенной галкой `Писать в клан чат о Боссе...` дождаться события и убедиться, что появляется строка:
   - `%clan% "<boss>" возможно на клетках: ... в бою ...`
2. Проверить, что `я нападаю на перса ...` показывает `клетка № XX-XXX`.
3. Проверить, что серверные `NeverLands.Ru` строки теперь всегда с `timestamp-server` в начале.
4. По кейсу возврата поймать повтор `return_timeout` и снять лог для дофикса возврата.

