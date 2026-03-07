# Задача: порт `buttonFury` (Снежок/Ярость) из C# + авто-надевание свитка в Android AutoBoi

## Цель
- [x] Перенести 1:1 поведение ПК-кнопки `buttonFury` в Android как чекбокс во вкладке `Авто-бой -> Общие`.
- [x] Добавить авто-надевание свитка (`Свиток Удар Ярости` / `Снежок`) по аналогии с `AutoSkin` (нож), чтобы к первому удару свиток был надет.
- [ ] Сохранить совместимость с текущим пайплайном `MainPhp`/`LezFight`/настроек профиля.

## Эталонные источники (C#)
- [x] `ABClient/ABForms/FormMain.Designer.cs`:
  - tooltip `buttonFury`: `Снежок или ярость (первый удар на осаде)`.
- [x] `ABClient/ABForms/FormMain.cs`:
  - `buttonFury_Click`: включает/выключает `AppVars.DoFury` и пишет сообщение в чат.
- [x] `ABClient/AppVars.cs`:
  - глобальный флаг `DoFury`.
- [x] `ABClient/Lez/LezFight.cs`:
  - `IsMagicAllowed`: scroll-hit разрешён только при `DoFury && IsBossName(...)`;
  - детект попадания `Свиток Удар Ярости`/`Снежок` по логам;
  - после первого scroll-hit: `FuryOff + AutoboiOff + чат`.
- [x] `ABClient/ABForms/FormMainCross.cs`:
  - `FuryOff()` снимает флаг/чекбокс.
- [x] `ABClient/PostFilter/MainPhp.cs`:
  - `if (!AppVars.DoFury) ...` (сопутствующая развилка потоков `main.php`).

## HAR-референсы
- [x] `DressSvitok.har`:
  - надевание через `GET main.php?get_id=57&uid=...&s=1&vcode=...`.
- [x] `1AttackSvitok.har`:
  - первый удар свитком уходит в боевой `POST main.php` с `post_id=7`;
  - в `ina` присутствует код `277` (`...ina=277_<uid>@...`).

## Точки Android для правок
- [x] `app/src/main/java/ru/neverlands/abclient/model/UserConfig.java`
  - новый профильный флаг `LezDoFury` + XML load/save в секции `<autoboi ...>`.
- [x] `app/src/main/java/ru/neverlands/abclient/utils/AppVars.java`
  - runtime-флаги `DoFury` + состояние авто-проверки/надевания свитка.
- [x] `app/src/main/java/ru/neverlands/abclient/ui/AutoBoiSettingsFragment.java`
  - чекбокс в `GeneralTabFragment` (load/save + синхронизация runtime).
- [x] `app/src/main/res/layout/tab_autoboi_general.xml`
  - UI чекбокса `Снежок или ярость (первый удар на осаде)`.
- [x] `app/src/main/java/ru/neverlands/abclient/LoginActivity.java`
  - инициализация `AppVars.DoFury` и runtime-флагов после входа.
- [x] `app/src/main/java/ru/neverlands/abclient/model/ParsedDressed.java`
  - проверка, надет ли нужный свиток.
- [x] `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
  - ветка авто-надевания свитка (по аналогии с AutoSkin): проверка экипировки -> переход в инвентарь (`im=0&wca=28`) -> wear-link.
- [x] `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java`
  - 1:1 логика `DoFury` в `IsMagicAllowed`;
  - детект попадания свитком по логам;
  - авто-выключение `DoFury` и `Авто-бой` после первого scroll-hit.

## План реализации
1. [x] Добавить модель/хранение флага:
   - `UserConfig.LezDoFury` (default `false`);
   - load/save attr `fury` в теге `<autoboi>`.
2. [x] Добавить UI:
   - чекбокс на вкладку `Общие`;
   - привязка в `loadSettings()/saveSettings()`.
3. [x] Добавить runtime-состояние:
   - `AppVars.DoFury`;
   - флаги авто-проверки/авто-надевания свитка.
4. [x] Реализовать авто-надевание свитка в `MainPhp`:
   - проверка экипировки через `ParsedDressed`;
   - поиск wear-ссылки в инвентаре по именам `Свиток Удар Ярости`/`Снежок`;
   - редирект на надевание по HAR-паттерну.
5. [x] Реализовать боевую часть в `LezFight`:
   - разрешить scroll-hit при `DoFury && IsBoss()`;
   - детект факта удара свитком по боевым логам;
   - сразу выключать `DoFury` и `Авто-бой`, писать статус в чат.
6. [x] Прогнать компиляцию:
   - `./gradlew :app:compileDebugJavaWithJavac`.

## Критерии готовности
- [ ] В `Авто-бой -> Общие` есть чекбокс режима свитка осады, сохраняется в профиль.
- [ ] При включённом режиме и ненадетом свитке клиент сам доходит до `wear`-запроса и надевает нужный свиток.
- [ ] Первый удар в бою использует `Снежок`/`Свиток Удар Ярости` (когда доступно и цель boss).
- [ ] После первого успешного scroll-hit режим свитка и автобой отключаются автоматически.
- [ ] Сборка проходит без регрессий.

## Выполнено в этой итерации
- [x] Создан новый профильный флаг `LezDoFury` с XML атрибутом `autoboi@fury` (load/save).
- [x] Добавлен чекбокс режима осады на вкладку `Общие` и сохранение в профиль.
- [x] Добавлены runtime-флаги Fury в `AppVars` и инициализация после логина.
- [x] В `ParsedDressed` добавлена проверка надетого свитка `IsWearFuryScroll()`.
- [x] В `MainPhp` добавлена оркестрация авто-надевания свитка (`&im=0&wca=28` -> wear-link).
- [x] В `LezFight` добавлен C#-паритет:
  - scroll-hit разрешается при `DoFury && IsBoss()`,
  - по факту первого scroll-hit режим Fury и AutoBoi выключаются автоматически.
- [x] Компиляция: `:app:compileDebugJavaWithJavac` — `BUILD SUCCESSFUL`.
