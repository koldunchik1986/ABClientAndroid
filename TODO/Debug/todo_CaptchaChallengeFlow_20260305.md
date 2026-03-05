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
- [ ] Сервер возвращает post-fight страницу, где без заполнения `code` нет перехода в обычный `main.php`.

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
1. [ ] Собрать 3-5 runtime-логов с разными сценариями:
   - [ ] бой без captcha
   - [ ] бой с captcha
   - [ ] повторный бой после успешного ввода captcha
2. [ ] Для каждого сценария выделить последовательность URL/POST и финальное состояние.
3. [ ] Построить таблицу переходов состояний:
   - [ ] `state_id`
   - [ ] `server markers`
   - [ ] `client action`
   - [ ] `expected next page`
   - [ ] `actual result`
4. [ ] Найти точку, где клиент неверно классифицирует captcha-required как обычный finish.

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
- [ ] Нет бесконечного цикла `main.php -> fight-frame -> main.php`.
- [ ] Для "без captcha" завершение боя стабильно уходит в обычный `main.php`.
- [ ] Для "captcha required" клиент корректно останавливается на вводе captcha и после успешного ввода продолжает обычный поток.
- [ ] В логах есть однозначный маркер, почему выбрана конкретная ветка завершения.

## Файлы для правок/проверки
- [x] `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
- [x] `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`
- [ ] `TODO/Debug/todo_ServerCaptchaResponseAnalysis_20260305.md` (сводный вывод по мере выполнения)
