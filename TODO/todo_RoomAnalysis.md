# Анализ функциональности "Комнаты"

## 1. Введение

Этот документ содержит подробный анализ функциональности, связанной с окном комнаты в ПК-версии клиента, и сравнивает ее с текущей реализацией в Android-версии. Основное внимание уделяется отсутствующим элементам, таким как кнопки быстрых действий, и разработке плана по их портированию.

## 2. Анализ ПК-версии (C#)

### 2.1. `RoomManager.cs`

- **Основная задача**: Этот класс в фоновом потоке периодически запрашивает данные о комнате (`ch.php`), парсит HTML и обновляет информацию об игроках.
- **Модификация HTML**: Метод `Process` в `RoomManager.cs` активно модифицирует HTML-код комнаты. Он добавляет CSS-стили и, что более важно, внедряет большой блок JavaScript.
- **Кнопки быстрых действий**: Метод `HtmlChar` генерирует HTML для каждого игрока в списке. Именно здесь создаются иконки (кнопки) для быстрых действий. Каждая кнопка представляет собой ссылку (`<a>`) с вызовом JavaScript-функции `window.external`. Например:

```csharp
wmlabQ = " <a class=\"activeico\" href=\"javascript:window.external.Quick('" + login + "')\"><img src=http://image.neverlands.ru/signs/c227.gif ...></a>";
wmlabFA = " <a class=\"activeico\" href=\"javascript:window.external.FastAttack('" + login + "')\"><img src=http://image.neverlands.ru/weapon/i_svi_001.gif ...></a>";
```

- **`window.external`**: Это ключевой механизм для взаимодействия между JavaScript в `WebView` и нативным кодом C#. Методы, вызываемые через `window.external` (например, `Quick`, `FastAttack`), реализованы в `FormMain.cs` и помечены как `[ComVisible(true)]`.

### 2.2. `FormMain.cs`

- **Обработка вызовов из JavaScript**: `FormMain.cs` содержит реализацию методов, которые вызываются из JavaScript через `window.external`. Например, `public void Quick(string nick)`. 
- **Выполнение действий**: Эти методы, в свою очередь, инициируют соответствующие действия в игре, такие как быстрое нападение, использование свитков и т.д.

## 3. Анализ Android-версии (Java)

### 3.1. `RoomManager.java`

- **Текущее состояние**: На данный момент это класс-заглушка. Метод `process` просто возвращает исходный HTML без каких-либо изменений.
- **Отсутствующая логика**: Вся логика по парсингу игроков, генерации кнопок быстрых действий и внедрению JavaScript отсутствует.

### 3.2. `Filter.java`

- **Перехват запросов**: Этот класс корректно перехватывает ответы от сервера, включая `ch.php`.
- **Вызов `RoomManager.process`**: Я добавил вызов `RoomManager.process`, но так как сам метод `process` пока пуст, никаких изменений не происходит.

### 3.3. `MainActivity.java`

- **JavaScript-интерфейс**: В `MainActivity.java` есть JavaScript-интерфейс `WebAppInterface`. Однако он не содержит методов, необходимых для быстрых действий (`Quick`, `FastAttack` и т.д.).

## 4. Сравнение и выводы

Основная проблема заключается в том, что логика `RoomManager.cs` не была портирована на Java. В Android-версии отсутствует как код для модификации HTML и добавления кнопок, так и нативная реализация методов, которые эти кнопки должны вызывать.

Ошибка `Uncaught SyntaxError: Unexpected identifier 'target'`, которую мы наблюдали, является следствием этой проблемы. `ch_list.js` ожидает, что `window.external` будет доступен, но его нет. Мои предыдущие попытки исправить это были направлены на устранение симптомов, а не основной причины.

## 5. План портирования

### Шаг 1: Реализация `RoomManager.java`

- [ ] **Портировать `FilterProcRoom` и `HtmlChar`**: Перенести логику из `RoomManager.cs` в `RoomManager.java`. Вместо `HelperStrings.SubString` использовать регулярные выражения или `Jsoup` для парсинга HTML.
- [ ] **Генерация кнопок**: В методе, аналогичном `HtmlChar`, генерировать HTML-код для кнопок быстрых действий. Вместо `window.external` мы будем использовать JavaScript-интерфейс, который мы создадим на следующем шаге. Например:

```java
String wmlabQ = " <a class=\"activeico\" href=\"javascript:AndroidBridge.quick('" + login + "')\"><img src=http://image.neverlands.ru/signs/c227.gif ...></a>";
```

- [ ] **Обновление `process`**: Метод `process` в `RoomManager.java` должен будет выполнять полную обработку HTML, включая вставку сгенерированных кнопок.

### Шаг 2: Расширение JavaScript-интерфейса

- [ ] **Добавить методы в `WebAppInterface.java`**: Для каждого метода, вызываемого из `window.external` в C# версии, необходимо добавить соответствующий метод в `WebAppInterface.java` с аннотацией `@JavascriptInterface`. Например:

```java
@JavascriptInterface
public void quick(String nick) {
    // TODO: Implement quick action logic
}

@JavascriptInterface
public void fastAttack(String nick) {
    // TODO: Implement fast attack logic
}
```

### Шаг 3: Интеграция и тестирование

- [ ] **Проверить, что `Filter.java` корректно вызывает `RoomManager.process`**.
- [ ] **Протестировать**, что кнопки быстрых действий отображаются в списке игроков и что при нажатии на них вызываются соответствующие методы в `WebAppInterface.java`.

Я начну с реализации Шага 1.
