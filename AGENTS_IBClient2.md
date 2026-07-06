# Правила работы с IBClient2

Этот файл дополняет `AGENTS.MD` для задач по `IBClient2`. Перед любой работой с `IBClient2` читать оба файла: сначала `AGENTS.MD`, затем `AGENTS_IBClient2.md`.

## Главная цель

- Финальная цель: заставить `IBClient2.exe` штатно подгружать оригинальный `iBClient.Plugin.Forest.dll` и выставлять license/feature state так, чтобы проверка по нику/ответу сервера не отключала Forest plugin.
- Оригинальный `iBClient.Plugin.Forest.dll` считается рабочим источником функционала `Автолес`: он сам должен выполнять UI, hooks, `Wear`, `Start`, `Empty`, captcha, resource loop и прочую логику.
- Править DLL как финальное решение нельзя. Если DLL трогали раньше, это был диагностический обход, не целевая архитектура.

## Что править

- Основной объект исправления: `IBClient2.exe` license/state/load contour.
- Нужно искать и править существующие decision points проверки лицензии, feature mask, дат и загрузки plugins.
- Целевое состояние после проверки: Forest feature bit разрешен, даты валидны, переменные лицензии выставлены в разрешающие значения.
- Из известных license-state символов/полей: `ht9IJmZTVf`, `rXZIdbZ7Rh`, `KolIKMKPmA`, `GGdIMaCSy6`, `Y61IvcGT4u`, `YF1IYKx8us`.
- Оригинальный Forest plugin имеет `RequiredFeature=4`, поэтому итоговый feature mask должен включать bit `4`; проверенный рабочий mask для диагностики: `FeatureFlags=7`.

## Что не править

- Не развивать `restored_forest_project/iBClient/Plugin/Forest/ForestPlugin.cs` как финальный путь.
- Не добавлять новые JS fallback/loop hooks в `map.js` как финальное решение.
- Не считать `patch_forest_map_cache.ps1` финальным способом включения `Автолес`; это только диагностический/временный инструмент.
- Не заменять оригинальную логику плагина восстановленной DLL, self-register toolbar, JS-only fallback или browser-side state machine.
- Не переписывать `IBClient2.exe` через `dnlib module.Write(...)`: уже подтверждена launch regression на защищенном EXE.

## Текущий статус DLL

- Оригинальный DLL восстановлен в runtime locations:
  - `IBClient2/iBClient.Plugin.Forest.dll`
  - `IBClient2/Plugins/iBClient.Plugin.Forest.dll`
- Оригинальный DLL hash/size:
  - size `943616`
  - SHA256 `2C2611C7C868E24FDB07F24FCB3C38F1D7C48CA2CF4BFD80D2981750D808A2BB`
  - reflection: `ForestPlugin` найден, assignable к `iBClient.PluginHost.IIBClientPlugin`, `RequiredFeature=4`.
- Backup оригинального DLL:
  - `IBClient2/iBClient.Plugin.Forest.dll.bak_before_restored_20260605_172712`
  - `IBClient2/Plugins/iBClient.Plugin.Forest.dll.bak_before_restored_20260605_172712`
- Backup последней восстановленной/диагностической DLL:
  - `IBClient2/iBClient.Plugin.Forest.dll.bak_rebuilt_20260606_162509`
  - `IBClient2/Plugins/iBClient.Plugin.Forest.dll.bak_rebuilt_20260606_162509`
  - size `1292800`
  - SHA256 `3907456C9865A7CA8819783250D7D95A9545D071EE3B0CC0D1742A1E59B61D08`
- Если дальнейшая работа требует DLL, использовать оригинальный DLL из runtime locations, не rebuilt diagnostic DLL.

## Текущий статус EXE

- Рабочий root EXE не перезаписывать без явного backup и успешного smoke candidate:
  - `IBClient2/IBClient2.exe`
- Известный рабочий backup root EXE:
  - `IBClient2/IBClient2.exe.bak_20260605_094050`
- Текущий preferred candidate после подтверждения stock plugin manager/host contour и server-response blocker для `Блудя`:
  - `IBClient2/IBClient2_forest_license_response_candidate.exe`
  - SHA256 `264714CF1CD0498B1C365EA5F8874CA616848FA04D7FFDA538585C3D85FCB40B`
  - size `2316800`
  - `PatchForestRawBridge=False`
  - `PatchLicenseResponseParse=True`
  - `FeatureFlags=7`
  - license dates `2028-12-31 23:59:59`
  - сохраняет original load/host contour: `OAbdPdV3Jq`/`y6bdp773lh` остаются protected stock stubs (`IL=4` offline).
  - raw-патчит `pGppETIEYE7rWKO8Od1.xnVIwBRw7p` (`0x06000311`), чтобы parsing server license response возвращал `out FeatureMask=7` и будущую дату; manual smoke: `Блудя` запускает `Автолес` успешно.
  - подробный runbook: `IBClient2/instructions/patch_license_check_binary.md`.
- Предыдущий license-only candidate, который работает только когда server response уже разрешает Forest:
  - `IBClient2/IBClient2_forest_license_only_stockload_candidate.exe`
  - SHA256 `BAB0696A9C92C42E190FB5153E533CB6B3DFDD09044419C8BCBB699931D44CD9`
  - size `2316288`
  - `PatchForestRawBridge=False`
  - `FeatureFlags=7`
  - license dates `2028-12-31 23:59:59`
  - сохраняет original load/host contour, но не патчит server response parse; для `Блудя` trace ловит hotkey, но `Автолес` не открывается из-за server `FeatureMask=3`.
- Диагностический raw bridge candidate без `Start` fallback, не текущий final path:
  - `IBClient2/IBClient2_forest_raw_bridge_no_hotkey_start_rebuilt_candidate.exe`
  - SHA256 `28F123D3663FC492C6EE7CB9791F316CF1127D5398458B9219DB9EA9D5CE5BEC`
  - size `2317824`
  - `FeatureFlags=7`
  - license dates `2028-12-31 23:59:59`
  - `ForestRawBridgeBlobLength=797`
- Отклонённый init-candidate для проверки появления toolbar-кнопки оригинальной DLL:
  - `IBClient2/IBClient2_forest_raw_bridge_init_candidate.exe`
  - SHA256 `4B28F1FA68AA4C7840CBD8B76D00BC59C2AFC084C8EBF89FE9E45A2BEED8D863`
  - size `2317824`
  - `FeatureFlags=7`
  - license dates `2028-12-31 23:59:59`
  - `ForestRawBridgeBlobLength=877`
  - `OAbdPdV3Jq` дополнительно делает deferred `ForestPlugin.Initialize(IPluginHost host)` под catch и добавляет plugin в список только после успешного init.
- Отклонённый host-adapter candidate для проверки штатного `Initialize(host) -> AddMainToolStripItem`:
  - `IBClient2/IBClient2_forest_raw_bridge_hostadapter_candidate.exe`
  - SHA256 `017E231C4F566EE1C5D12B97A939263C7857F804B1554B29883EB361BFB80782`
  - size `2317824`
  - `FeatureFlags=7`
  - license dates `2028-12-31 23:59:59`
  - `ForestRawBridgeBlobLength=1001`
  - `AIpC4AdRdRQ6pfP6nVi.get_MainForm/AddMainToolStripItem/RemoveMainToolStripItem` raw-patched; `OAbdPdV3Jq(Form)` creates this existing host adapter and passes it to original `ForestPlugin.Initialize(host)`.
- Ранние кандидаты/пути, которые не использовать как финальные:
  - `IBClient2_forest_raw_bridge_candidate.exe`: crash после `Ctrl+Alt+Z` из-за unsafe `Start`/AntiCaptcha thread.
  - `IBClient2_forest_raw_bridge_host_candidate.exe`: отклонен, host incomplete/stub; ручная проверка дала `NullReferenceException` в `iBClient.Plugin.Forest.ForestPlugin.Initialize(IPluginHost host)` при `Ctrl+Alt+Z`.
  - Любой `dnlib module.Write(...)` output: launch regression на protected EXE.

## Правила патчинга EXE

- Использовать существующий raw/in-place contour: `IBClient2/scripts/patch_license_check_binary.ps1`.
- Для текущей Forest/license-response задачи использовать подробный runbook `IBClient2/instructions/patch_license_check_binary.md` и switch `-PatchLicenseResponseParse`.
- Не создавать параллельный patcher, если можно исправить существующий raw contour.
- Не менять metadata table counts и не выполнять full PE rewrite.
- Предпочтительный подход: patch protector/runtime-table/license values или raw MethodDef RVA/code-cave, без `dnlib module.Write(...)`.
- Перед перезаписью root `IBClient2.exe` обязательно:
  - создать backup с timestamp;
  - собрать отдельный candidate;
  - проверить reflection/JIT license state;
  - провести GUI smoke из настоящей папки `IBClient2`;
  - только после этого обсуждать deployment.

## Правила GUI smoke

- Smoke запускать только из настоящей папки:
  - `C:\Users\User\AbclientAndroid\IBClient2`
- Причина: рабочий профиль входа лежит именно в корне `IBClient2`; при запуске из другой папки клиент не видит профиль и может зависнуть на окне создания профиля, поэтому такой smoke невалиден.
- Нельзя использовать `IBClient2/forest_hotkey_test` как валидный smoke environment.
- Обычный smoke НЕ должен открывать окно настроек `Автолес`: не отправлять hotkey и не считать появление окна успешным критерием.
- Основной критерий обычного smoke: после входа в игру в главном toolbar/UI появляется кнопка `Автолес`, процесс жив, crash events отсутствуют.
- `Ctrl+Alt+Z` использовать только для отдельной явной диагностики hotkey/settings path, не для штатного smoke проверки загрузки plugin.
- Перед `Ctrl+Alt+Z` обязательно дождаться входа в игру: минимум 15 секунд, обычно 15-20 секунд; hotkey отправлять только после того, как окно уже не `Быстрый вход` и клиент находится в игровом состоянии.
- Если через 20 секунд всё ещё открыт `Быстрый вход`, `Ошибка загрузки профайла`, окно создания профиля или вход не завершён, `Ctrl+Alt+Z` не отправлять; сначала диагностировать логин/профиль.
- После hotkey/клика ждать минимум 20 секунд.
- Для `Ctrl+Alt+Z` использовать real `user32/keybd_event`; `SendKeys("^%z")` ненадежен и уже давал ложный fail.
- Нельзя использовать путь `Ctrl+Alt+Z -> Start`: это запускало unsafe AntiCaptcha thread и падение.
- Успешный smoke должен подтверждать не только отсутствие crash, но и что original plugin реально загрузился/активен в штатном host контуре.
- Neverlands ограничивает частые входы: больше 2-3 входов в минуту под одним персонажем может заблокировать вход примерно на 30 минут. GUI smoke запускать с cooldown: не более 2 login attempts/minute на персонажа, лучше выдерживать 60-120 секунд между запусками; при серии неудачных входов остановиться и не продолжать автологины.
- Для диагностики предпочитать уже открытую сессию и offline/reflection checks; не перезапускать клиент ради каждого мелкого наблюдения.
- `trace_ibclient2_forest_live.ps1` safe-mode не должен использовать deep UIA descendant scan по WebBrowser/COM tree. Deep scan вынесен в явный `-UnsafeDeepUiScan`, потому что уже вызывал crash `OLEAUT32.dll`/`0xc00000fd`. Для original root подтверждено вручную и trace'ом: `Ctrl+Alt+Z` ловится, foreground переходит в `Настройки клиента`, `Автолес` UI появляется/пропадает, crash events `0`.

## Правила проверки лицензии/feature state

- Проверка сервера по текущему профилю ранее давала payload `Блудя|2026-08-30|3`; `FeatureMask=3` не включает Forest bit `4`.
- Для цели `Автолес` EXE должен после проверки иметь разрешающий state независимо от этого server payload.
- Диагностический целевой state:
  - `FeatureFlags=7`
  - Forest bit `4` enabled
  - даты лицензии не истекли
  - `RequiredFeatureEnabled=True` для original DLL `RequiredFeature=4`
- Не надо подменять работу оригинального DLL, если EXE license/state уже корректен.

## Рабочий порядок следующей сессии

1. Перечитать `AGENTS.MD` и `AGENTS_IBClient2.md`.
2. Открыть `IBClient2/instructions/patch_license_check_binary.md` как актуальный runbook для Forest/license-response patch.
3. Убедиться, что runtime DLL снова оригинальный (`size=943616`, SHA256 `2C2611...`).
4. Не трогать `restored_forest_project` и `map.js` без отдельной диагностической причины.
5. Проверить текущий response-parse candidate с original DLL: `PatchLicenseResponseParse=True`, `PatchForestRawBridge=False`, reflection/JIT на license fields, `xnVIwBRw7p` и `RequiredFeatureEnabled=True`.
6. Если candidate проходит static checks, провести GUI smoke из настоящего `IBClient2`.
7. Если original DLL не грузится, искать проблему в EXE plugin manager/host/license path, а не в DLL.
8. Deployment поверх `IBClient2.exe` делать только после backup и подтвержденного smoke.

## Запрещенные выводы

- Не считать, что проблема в DLL, пока не доказано, что original DLL получает корректный host/license state и все равно не работает.
- Не продолжать browser-side loop/fallback как замену оригинального plugin behavior.
- Не использовать rebuilt diagnostic DLL как финальную.
- Не смешивать Android-porting задачи и IBClient2 binary/runtime задачи.
