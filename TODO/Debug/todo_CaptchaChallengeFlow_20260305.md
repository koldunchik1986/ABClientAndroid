# TODO: Полный анализ captcha-flow сервера (2026-03-05)

## Контекст
- По `Logs/logcat_runtime_20260305_04.txt` не видно старого цикла `FightLink missing, redirecting to main.php`.
- В логе есть загрузка `js/fkey.js` и обращения к `d.FEND.code.value`, значит серверный captcha/finish-механизм присутствует в потоке.

## Цель
- Полностью разобрать, в каких ответах сервер требует captcha.
- Стабилизировать клиентский flow завершения боя и обработки captcha без зацикливания.
- Собрать доказуемую матрицу состояний "сервер -> клиент -> результат".

## Что считаем captcha-челленджем
- [x] В HTML есть форма `FEND` с полем `code` и/или обязательным вводом.
- [x] Подгружается `js/fkey.js` и активен keypad (`KeyInsert`, `BackKey`).
- [x] Сервер возвращает post-fight страницу, где без заполнения `code` нет перехода в обычный `main.php`.

## Матрица сигналов для логирования
- [x] `MainPhp.mainPhpFight`: зафиксировать `fight_ty`, `LogBoi`, `IsBoi`, `FightLink`, факт `fightEnded`.
- [x] `MainPhp`: отдельный лог-маркер веток:
  - [x] `fight-end path: use FightLink`
  - [x] `fight-end path: auto-submit FEND`
  - [x] `fight-end path: captcha required (manual)`
- [x] `WebViewInterceptor`: логировать краткий fingerprint страницы:
  - [x] есть/нет `form[name=FEND]`
  - [x] есть/нет `input[name=code]`
  - [x] есть/нет `js/fkey.js`
  - [x] action формы (без чувствительных данных)

## План анализа серверных ответов
1. [x] Собрать 3-5 runtime-логов с разными сценариями:
   - [x] бой без captcha
   - [x] бой с captcha
   - [x] повторный бой после успешного ввода captcha
2. [x] Для каждого сценария выделить последовательность URL/POST и финальное состояние.
3. [x] Построить таблицу переходов состояний:
   - [x] `state_id`
   - [x] `server markers`
   - [x] `client action`
   - [x] `expected next page`
   - [x] `actual result`
4. [x] Найти точку, где клиент неверно классифицирует captcha-required как обычный finish.

## План реализации (Android)
- [x] В `MainPhp` вынести явный `FinishFlowDecision`:
  - [x] `DIRECT_FINISH_LINK`
  - [x] `FEND_AUTOSUBMIT_ALLOWED`
  - [x] `CAPTCHA_REQUIRED`
- [x] Для `CAPTCHA_REQUIRED`:
  - [x] не делать авто-submit пустого/placeholder `code`;
  - [x] не редиректить fallback-циклом на `main.php`;
  - [x] оставлять страницу captcha и показывать понятный статус в лог/UI.
- [x] Добавить антизацикливание на уровне повторов одного `LogBoi + challenge hash`.

## Критерии готовности
- [x] Нет бесконечного цикла `main.php -> fight-frame -> main.php`.
- [x] Для "без captcha" завершение боя стабильно уходит в обычный `main.php`.
- [x] Для "captcha required" клиент корректно останавливается на вводе captcha и после успешного ввода продолжает обычный поток.
- [x] В логах есть однозначный маркер, почему выбрана конкретная ветка завершения.

## Обновление по логам 04/05/06

### Таблица переходов состояний

| state_id | server markers | client action | expected next page | actual result |
| --- | --- | --- | --- | --- |
| `S1_NO_CAPTCHA` | `fight ended`, `fightLink=get_id=61&act=7`, без `captchaUrl` | `DIRECT_FINISH_LINK` | обычный `main.php` | Цикла нет, завершение стабильно |
| `S2_CAPTCHA_WAIT` | `finishFlow=CAPTCHA_REQUIRED`, есть `code.php?token` | показ диалога капчи, `AutoboiOff`, `skip autoTurn` | ожидание ввода кода | Без submit поток корректно в ожидании |
| `S3_CAPTCHA_SUBMIT` | `showCaptchaDialog: submitting ...code=NNNNN...act=7`, HTTP `200` | submit по finish URL, восстановление `AutoboiOn` | переход в обычный post-fight поток | Поток продолжается, повторной captcha нет |

### Вывод по классификации
- Неверной классификации `captcha-required` как обычного finish не найдено.
- Ветка завершения выбирается корректно и однозначно логируется (`finishFlow: decision=...` + `[CAPTCHA_FLOW]`).

## Файлы для правок/проверки

- [x] `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
- [x] `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`
- [ ] `TODO/Debug/todo_ServerCaptchaResponseAnalysis_20260305.md` (сводный вывод по мере выполнения)
