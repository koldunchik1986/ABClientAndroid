# ДИАГНОСТИКА: Проблема капчи при авторыбалке

## 📍 ROOT CAUSE НАЙДЕНА!

### Механизм рыбалки с капчей в `FishAjaxPhp.java`

**Цепочка действий:**

1. **ACT=1 Запрос** - получить vcode и капчу-токен
   - Файл: `FishAjaxPhp.java::processFishAct1()`
   - Парсит response: `[1,"captcha_token","vcode",mass,...]`
   - if `captche_required`: показывает диалог с URL capcha и `finishUrl` (содержит **старый vcode**)

2. **CAPTCHA Dialog** - показывается пользователю
   - Файл: `FishAjaxPhp.java::showFishCaptchaDialogOnce()`
   - `finishUrl = "fish_ajax.php?act=2&primid=X&vcode=VCODE_FROM_ACT1&code=???"`
   - **Ключевой момент:** vcode здесь - это vcode из **ACT=1 ответа**

3. **Пользователь вводит капчу** и отправляет `act=2`
   - POST идёт на `fish_ajax.php?act=2&primid=X&vcode=STAGE_VCODE&code=ENTERED_CODE`

4. **SERVER Response к ACT=2**
   - **УСПЕХ:** Возвращает cooldown маркер `@[0,[2,294]]@`
   - **ОШИБКА:** Возвращает `"неверный код защиты"` или `"код защиты введен неверно"`

### ⚠️ Проблем:

#### Сценарий ошибки (ЧТО ПРОИСХОДИТ У ПОЛЬЗОВАТЕЛЯ):

```
act=1 response → vcode_OLD_123
         ↓
капча UI показывается → финальный URL с vcode_OLD_123
         ↓
пользователь вводит капчу → POST ?act=2&vcode=vcode_OLD_123&code=ENTERED
         ↓
Сервер проверяет: vcode_OLD_123 УСТАРЕЛ!
         ↓
Server: "неверный код защиты" 
         ↓
FishAjaxPhp.process() обнаруживает ошибку
         ↓
requestSoftAutoFishRecovery("wrong_code_protection")
         ↓
loadUrl("http://neverlands.ru/main.php?get_id=56&act=10&...")
```

### 🔴 ГИПОТЕЗА ПРОБЛЕМЫ:

**После покупки капчи сервером присылается НОВЫЙ vcode в модуле для оплаты** (`modules/code/code.php`), НО:

1. **Мобильный клиент НЕ ПАРСИТ этот новый vcode** из ответа платежного модуля
2. **Модуль капчи работает через WebView/JS** (не через java-перехват)
3. Когда пользователь нажимает кнопку "Отправить" капчу в модуле, она отправляется с **СТАРЫМ vcode**, который был валидным только на момент `act=1`
4. Сервер отвергает старый vcode
5. Результат - бесконечный цикл

### 📋 Места обработки капчи в коде:

| Файл | Метод | Строка | Назначение |
|------|-------|--------|----------|
| FishAjaxPhp.java | processFishAct1 | ~200 | Парсит act=1, показывает капчу |
| FishAjaxPhp.java | showFishCaptchaDialogOnce | ~348 | Отправляет broadcast на показ капчи |
| FishAjaxPhp.java | containsFishWrongProtectionCode | ~653 | Проверяет ошибку защиты |
| FishAjaxPhp.java | requestSoftAutoFishRecovery | ~574 | Инициирует recovery при ошибке |
| MainPhp.java | showFightCaptchaDialogOnce | ~261 | Для боя (аналогично) |

### 🔧 РЕШЕНИЕ:

Нужна **синхронизация vcode между act=1 ответом и модулем платежа капчи**. 

**Вариант 1 (быстро):** Парсить ответ из `modules/code/code.php` и извлечь новый `vcode`
- Когда пользователь платит за капчу, он получает ответ с новым vcode
- Java-код должен перехватить этот ответ и обновить `AppVars.FishCurrentVcode`
- При отправке `act=2` использовать свежий vcode вместо старого

**Вариант 2 (надёжно):** JS-интеграция через WebView Bridge
- Когда в JS отправляется `act=2`, он должен **получить текущий vcode от Java-кода**
- Java хранит актуальный vcode, JS использует его при отправке

**Вариант 3 (обходной путь):** Увеличить TTL (время жизни) vcode на сервере
- Согласиться с разработчиком Neverlands, чтобы vcode действовал дольше

### 🎯 Текущий статус:

**ЭТАП 1 (Завершён):** Logger refactoring
- ✅ FileLogger unified
- ✅ Project builds

**ЭТАП 2 (ТРЕБУЕТСЯ):** Фикс синхронизации vcode при капче
- [ ] Поиск где парсится ответ модуля `modules/code/code.php` 
- [ ] Добавить парсинг нового vcode из этого ответа
- [ ] Сохранить в `AppVars` или в специальную переменную
- [ ] Использовать свежий vcode при `act=2`
- [ ] Тестирование авторыбалки с покупкой капчи

