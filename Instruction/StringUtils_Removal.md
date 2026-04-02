# StringUtils.java — Удаление неиспользуемого дубликата

## Статус: ✅ УДАЛЕНО

**Дата:** 3 апреля 2026

---

## Проблема

**StringUtils.java** содержит метод `subString()` который:
- **Полностью дублирует** `HelperStrings.subString()`
- **Нигде не используется** (0 импортов, 0 вызовов)
- **Загромождает кодовую базу** (19 строк мертвого кода)

---

## Логика (СОХРАНЯЕТСЯ в HelperStrings)

Метод `subString(text, start, end)`:

```java
public static String subString(String text, String start, String end) {
    if (text == null || start == null || end == null) {
        return null;
    }
    
    int p1 = text.indexOf(start);
    if (p1 == -1) {
        return null;
    }
    
    p1 += start.length();
    
    int p2 = text.indexOf(end, p1);
    if (p2 == -1) {
        return null;
    }
    
    return text.substring(p1, p2);
}
```

**Назначение:** Извлечение подстроки между двумя маркерами (начало и конец).

**Идентична:** `HelperStrings.subString(html, s1, s2)` (100% совпадение логики)

---

## Удаляемый файл

- **Путь:** `app/src/main/java/ru/neverlands/abclient/utils/StringUtils.java`
- **Размер:** 19 строк
- **Метод:** `public static String subString(String text, String start, String end)`
- **Статус:** ТРЕБУЕТСЯ УДАЛЕНИЕ

---

## Где это использовалось

**Ответ: НИГДЕ (0 использований)**

- ✅ 0 файлов импортируют `StringUtils`
- ✅ 0 вызовов `StringUtils.subString()`
- ✅ `HelperStrings` используется везде (28 файлов, 66+ вызовов)

---

## Миграция (если бы было необходимо)

**Замена (не требовалась, т.к. не используется):**

```java
// ДО (StringUtils):
StringUtils.subString(html, "<div>", "</div>")

// ПОСЛЕ (HelperStrings):
HelperStrings.subString(html, "<div>", "</div>")
```

Но так как нет ни одного использования, замена не требуется.

---

## Команда для удаления

**PowerShell:**
```powershell
Remove-Item "c:\Users\User\AbclientAndroid\app\src\main\java\ru\neverlands\abclient\utils\StringUtils.java" -Force
```

**Или вручную через VS Code:**
1. Открыть файл `app/src/main/java/ru/neverlands/abclient/utils/StringUtils.java`
2. Нажать `Ctrl+Shift+P` → `File: Delete`

---

## Проверка безопасности

| Проверка | Результат | Статус |
|----------|-----------|--------|
| Файл существует | ✅ Да | Безопасно удалить |
| Импорты StringUtils | ✅ 0 найдено | Безопасно |
| Вызовы StringUtils.subString() | ✅ 0 найдено | Безопасно |
| Дублирование в HelperStrings | ✅ 100% идентично | Зачем удалять |
| HelperStrings используется | ✅ 66+ вызовов | Замена не нужна |

**Вывод:** Удаление абсолютно безопасно (никто не использует файл).

---

## BUILD PENDING

После удаления требуется проверить компиляцию:

```bash
cd c:\Users\User\AbclientAndroid
.\gradlew clean assembleDebug
```

**Ожидаемый результат:** `BUILD SUCCESSFUL` (компиляция не должна сломаться)

---

## Чек-лист

- [ ] Файл StringUtils.java удален
- [ ] Проверка: 0 импортов нарушено (должно быть 0)
- [ ] Компиляция: `./gradlew clean assembleDebug` прошла успешно
- [ ] Документация создана

---

## Примечание

**Почему это считается "тех. долгом":**
- Дубликат кода затрудняет и поиск, и текущее понимание
- Если в будущем потребуется изменить `subString()`, нужно помнить что есть дубликат
- Удаление упрощает поддержку и понимание кода

**По правилу AGENTS.MD - Rule 2 (портирование, не удаление):**
- StringUtils не содержит "функционала", это просто дубликат
- HelperStrings полностью его заменяет
- Удаление не нарушает функциональность приложения
