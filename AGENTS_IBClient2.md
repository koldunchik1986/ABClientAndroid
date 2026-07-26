# Правила работы с IBClient2

Этот файл дополняет `AGENTS.MD` для задач по `IBClient2`. Перед любой работой с `IBClient2` читать оба файла: сначала `AGENTS.MD`, затем `AGENTS_IBClient2.md`.

## Главная цель

- Финальная цель: заставить `IBClient2.exe` штатно подгружать оригинальный `iBClient.Plugin.Forest.dll` и выставлять license/feature state так, чтобы проверка по нику/ответу сервера не отключала Forest plugin.
- Оригинальный `iBClient.Plugin.Forest.dll` считается рабочим источником функционала `Автолес`: он сам должен выполнять UI, hooks, `Wear`, `Start`, `Empty`, captcha, resource loop и прочую логику.
- Править DLL как финальное решение нельзя. Если DLL трогали раньше, это был диагностический обход, не целевая архитектура.

## Обязательная фиксация находок в Markdown

- Все новые выводы по `IBClient2` сразу фиксировать в `IBClient2/instructions/*.md`, чтобы следующая сессия не зависела от чата/share.
- Токены, поля, MethodDef/MemberRef, RVA/key, hashes и результаты reflection/search записывать в `IBClient2/instructions/new_version_tokens.md` или профильный runbook сразу после обнаружения.
- Изменения рабочего порядка, запреты, следующий шаг и статус ветки записывать в `IBClient2/instructions/new_version_plan.md` и/или этот файл.
- Если находка влияет на patcher/runbook, сразу обновлять `IBClient2/instructions/patch_license_check_binary.md` или соответствующий `instructions/scripts/*.md`.
- Не оставлять состояние вида "найдено только в переписке". Перед патчингом, smoke или финальным ответом проверить, что актуальные выводы уже перенесены в Markdown.

## Что править

- Основной объект исправления: `IBClient2.exe` license/state/load contour.
- Нужно искать и править существующие decision points проверки лицензии, feature mask, дат и загрузки plugins.
- Целевое состояние после проверки: Forest feature bit разрешен, даты валидны, переменные лицензии выставлены в разрешающие значения.
- Из известных license-state символов/полей: `ht9IJmZTVf`, `rXZIdbZ7Rh`, `KolIKMKPmA`, `GGdIMaCSy6`, `Y61IvcGT4u`, `YF1IYKx8us`.
- Оригинальный Forest plugin имеет `RequiredFeature=4`, поэтому итоговый feature mask должен включать bit `4`; текущий рабочий mask для открытой сборки: `FeatureFlags=15`.

## Что не править

- Не развивать `restored_forest_project/iBClient/Plugin/Forest/ForestPlugin.cs` как финальный путь.
- Не добавлять новые JS fallback/loop hooks в `map.js` как финальное решение.
- Не считать `patch_forest_map_cache.ps1` финальным способом включения `Автолес`; это только диагностический/временный инструмент.
- Не заменять оригинальную логику плагина восстановленной DLL, JS-only fallback или browser-side state machine.
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

- Рабочий root EXE не перезаписывать без явного backup, успешной offline/static/JIT проверки candidate и явного одобрения:
  - `IBClient2/IBClient2.exe`
- Актуальная ветка discovery после восстановления сессии `XYHOLnnI` от 2026-07-16:
  - target `IBClient2/IBClient2.exe`
  - size `2386944`
  - SHA256 `F8979A4A9A7385E265F961E7391C41279E89F7FB1DD0A6395B08DDD4199C5130`
  - свежие maps: `IBClient2/runtime_new_version_2mb`, `IBClient2/comparison_new_version_2mb`, `IBClient2/decompiled_runtime_new_version_2mb_named`, `IBClient2/restored_project_new_version_2mb`
  - найденные license/plugin anchors зафиксированы в `IBClient2/instructions/new_version_tokens.md`.
- Текущий candidate для target `F8979A4...`:
  - `IBClient2/IBClient2_new_version_license_response_candidate.exe`
  - SHA256 `4D6DCE0441909EDCC0BEE55E7E4F2DF7405EC6220937A8FEAF3C38C8CFF38874`
  - size `2387456`
  - `PatchLicenseResponseParse=True`
  - `PatchForestRawBridge=False`
  - dynamic parser token `0x0600039E aeq6lyh1qp53u8I0qYI.f1ycPZhvC06EHxVijvA.ngXhYH0xkV`
  - `FeatureFlags=15`, license dates `2028-12-31 23:59:59`
  - offline/static/JIT validation passed: startup fields `True/True/15`, parser returns `true`, `OutFeatureInt=15`.
  - license/plugin gate подтверждён offline: `FeatureMask=15`, `RequiredFeature=4`, `RequiredFeatureEnabled=True`; root EXE не перезаписывался.
- Известный рабочий backup root EXE:
  - `IBClient2/IBClient2.exe.bak_20260605_094050`
- Исторический preferred candidate для прежней reference-сборки после подтверждения stock plugin manager/host contour и server-response blocker для `Блудя`:
  - `IBClient2/IBClient2_forest_license_response_candidate.exe`
  - SHA256 `264714CF1CD0498B1C365EA5F8874CA616848FA04D7FFDA538585C3D85FCB40B`
  - size `2316800`
  - `PatchForestRawBridge=False`
  - `PatchLicenseResponseParse=True`
  - использовал прежний diagnostic feature mask; для текущей сборки использовать `FeatureFlags=15`
  - license dates `2028-12-31 23:59:59`
  - сохраняет original load/host contour: `OAbdPdV3Jq`/`y6bdp773lh` остаются protected stock stubs (`IL=4` offline).
  - raw-патчит `pGppETIEYE7rWKO8Od1.xnVIwBRw7p` (`0x06000311`), чтобы parsing server license response возвращал разрешающий FeatureMask и будущую дату; для текущей сборки использовать mask `15`.
  - подробный runbook: `IBClient2/instructions/patch_license_check_binary.md`.
- Предыдущий license-only candidate, который работает только когда server response уже разрешает Forest:
  - `IBClient2/IBClient2_forest_license_only_stockload_candidate.exe`
  - SHA256 `BAB0696A9C92C42E190FB5153E533CB6B3DFDD09044419C8BCBB699931D44CD9`
  - size `2316288`
  - `PatchForestRawBridge=False`
  - использовал прежний diagnostic feature mask; для текущей сборки использовать `FeatureFlags=15`
  - license dates `2028-12-31 23:59:59`
  - сохраняет original load/host contour, но не патчит server response parse; для `Блудя` gate не проходит из-за server `FeatureMask=3`.
- Ранние кандидаты/пути, которые не использовать как финальные:
  - `IBClient2_forest_raw_bridge_candidate.exe`: crash из-за unsafe `Start`/AntiCaptcha thread.
  - `IBClient2_forest_raw_bridge_host_candidate.exe`: отклонен, host incomplete/stub; диагностика дала `NullReferenceException` в `iBClient.Plugin.Forest.ForestPlugin.Initialize(IPluginHost host)`.
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
  - проверить reflection/JIT license state и response parser;
  - подтвердить plugin gate: `FeatureMask=15`, `RequiredFeature=4`, `RequiredFeatureEnabled=True`;
  - только после этого обсуждать deployment.

## Правила проверки license/plugin gate

- Для запуска Forest plugin достаточно корректного license/feature state в EXE и штатного plugin manager/host contour.
- Основной критерий: `FeatureMask=15`, `RequiredFeature=4`, `RequiredFeatureEnabled=True`, даты лицензии валидны, response parser не возвращает server mask без Forest bit `4`.
- Проверять это offline/static/JIT и runtime/token maps: license fields, parser out values, `RequiredFeature` DLL и метод проверки feature bit.
- Интерактивные проверки не являются источником истины; не восстанавливать их как обязательный критерий.
- Live запуск клиента допустим только как общая проверка старта процесса после offline checks, но не как критерий включения Forest gate.

## Правила проверки лицензии/feature state

- Проверка сервера по текущему профилю ранее давала payload `Блудя|2026-08-30|3`; `FeatureMask=3` не включает Forest bit `4`.
- Для цели `Автолес` EXE должен после проверки иметь разрешающий state независимо от этого server payload.
- Диагностический целевой state:
  - `FeatureFlags=15`
  - Forest bit `4` enabled
  - даты лицензии не истекли
  - `RequiredFeatureEnabled=True` для original DLL `RequiredFeature=4`
- Не надо подменять работу оригинального DLL, если EXE license/state уже корректен.

## Рабочий порядок следующей сессии

1. Перечитать `AGENTS.MD` и `AGENTS_IBClient2.md`.
2. Открыть `IBClient2/instructions/new_version_plan.md` и `IBClient2/instructions/new_version_tokens.md`; все новые находки сразу дописывать туда.
3. Для target `F8979A4...` использовать только fresh maps из `runtime_new_version_2mb`; старые tokens/offsets применять только как исторические ориентиры после проверки shape/string.
4. Убедиться, что runtime DLL соответствует ожидаемому состоянию; если root и `Plugins` hashes расходятся, сначала зафиксировать это в `new_version_tokens.md` и выяснить, какой DLL реально грузится.
5. Не трогать `restored_forest_project` и `map.js` без отдельной диагностической причины.
6. Адаптировать существующий raw patch contour только после подтверждения новых license-state/response-parser/plugin-manager anchors.
7. Проверить `FeatureMask=15`, `RequiredFeature=4`, `RequiredFeatureEnabled=True` offline/static/JIT.
8. Если original DLL не грузится, искать проблему в EXE plugin manager/host/license path, а не в DLL.
9. Deployment поверх `IBClient2.exe` делать только после backup, offline/static/JIT подтверждения и явного одобрения.

## Запрещенные выводы

- Не считать, что проблема в DLL, пока не доказано, что original DLL получает корректный host/license state и все равно не работает.
- Не продолжать browser-side loop/fallback как замену оригинального plugin behavior.
- Не использовать rebuilt diagnostic DLL как финальную.
- Не смешивать Android-porting задачи и IBClient2 binary/runtime задачи.
