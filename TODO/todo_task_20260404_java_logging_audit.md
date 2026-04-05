# Аудит логирования Java (FileLogger + LogcatFileRecorder)

Дата: 04.04.2026 11:26:30

## Критерии проверки
- Некорректно: есть прямой `Log.*`, но нет `FileLogger.*` и нет `AppLog.*`.
- Корректно (этап миграции): используется `AppLog.*` (дублирует в logcat + FileLogger) либо прямой двойной контур `Log.*` + `FileLogger.*`.
- Исключения инфраструктуры фиксируются отдельно.

## Сводка
- Всего Java-файлов: **132**
- Файлы с прямым `Log.*` без `FileLogger.*`/`AppLog.*`: **1**
- Файлы с двойным контуром (`AppLog.*`/`Log.*` + `FileLogger`): **27**
- Файлы только с `FileLogger.*`: **3**
- Реальные нарушения к миграции: **0**

## Нарушения (требуется миграция)

| Статус | Файл | Log-вызовов | Примечание |
|---|---|---:|---|
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/RoomManager.java` | 57 | Высокий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/service/AutoModeForegroundService.java` | 36 | Высокий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/CompasAuto.java` | 25 | Высокий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/utils/ExtMap.java` | 22 | Высокий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/NeverApi.java` | 20 | Высокий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/LoginActivity.java` | 19 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java` | 15 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/TabManager.java` | 13 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/proxy/LocalHttpProxyServer.java` | 13 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/proxy/ProxyRuntimeManager.java` | 12 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/ClanWarsManager.java` | 10 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/QuickButtonsManager.java` | 9 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/CharacterVitalsManager.java` | 8 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/utils/DataManager.java` | 8 | Средний приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/ButPhp.java` | 7 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/MapActAjaxPhp.java` | 7 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/model/UserConfig.java` | 6 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/network/NetworkClient.java` | 6 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/utils/ChatStats.java` | 6 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/proxy/CookiesManager.java` | 5 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/ContactsActivity.java` | 4 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/proxy/Cache.java` | 4 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/utils/ForcedActionGuard.java` | 4 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/ChListJs.java` | 3 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/repository/ThingsRepository.java` | 3 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/GameJs.java` | 2 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/HpJs.java` | 2 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/MapJs.java` | 2 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/proxy/DiskCacheManager.java` | 2 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/proxy/ProxyLogDeduper.java` | 2 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/utils/WebViewProxyHelper.java` | 2 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/manager/UnderAttackManager.java` | 1 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/model/ParsedDressed.java` | 1 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/ChMsgJs.java` | 1 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/postfilter/FightJs.java` | 1 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/ui/AutoBoiSettingsFragment.java` | 1 | Низкий приоритет |
| [x] | `app/src/main/java/ru/neverlands/abclient/ui/Navigator.java` | 1 | Низкий приоритет |

## Инфраструктурные исключения (не считать нарушением)

| Статус | Файл | Причина |
|---|---|---|
| [-] | `app/src/main/java/ru/neverlands/abclient/utils/FileLogger.java` | Infrastructure logger; direct `android.util.Log` is acceptable for self-diagnostic. |

## Файлы только с FileLogger (инфо)

| Статус | Файл | FileLogger-вызовов | Комментарий |
|---|---|---:|---|
| [x] | `app/src/main/java/ru/neverlands/abclient/AuthManager.java` | 24 | Дополнительная миграция не требуется на этом этапе. |
| [x] | `app/src/main/java/ru/neverlands/abclient/WebViewCookieJar.java` | 5 | Дополнительная миграция не требуется на этом этапе. |

## Следующий шаг
- Миграция завершена: все файлы из списка переведены на `AppLog` (или уже имели `FileLogger`).
- В инфраструктурных файлах (`FileLogger`) прямой `android.util.Log` сохранён осознанно.
