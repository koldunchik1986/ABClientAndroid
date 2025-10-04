# Портирование и исправление обработки ch_list.js

**Статус:** `[ ]` В процессе

## 1. Анализ проблемы

Текущая реализация в Android-версии неверно обрабатывает скрипт `ch_list.js`, который отвечает за отображение списка персонажей в комнате. Вместо того чтобы использовать локальную, расширенную версию скрипта (как это делает ПК-версия), приложение загружает скрипт с сервера и пытается его модифицировать. Это приводит к двум основным проблемам:

1.  **Потеря функциональности:** Серверная версия `ch_list.js` не содержит вызовов к `window.external`, которые в ПК-версии используются для добавления кнопок быстрых действий, раскраски ников друзей/врагов и т.д.
2.  **Ошибки отображения:** Попытка внедрить массив `ChatListU` в скрипт вручную приводила к ошибкам из-за проблем с кодировкой, в результате чего список персонажей не отображался вовсе.

## 2. Логика работы в ПК-версии (C#)

В оригинальном клиенте `ABClient`:

1.  Запрос на `/ch/ch_list.js` перехватывается в `PostFilter\Filter.cs`.
2.  Вызывается метод `ChListJs()` в `PostFilter\ChListJs.cs`.
3.  Этот метод **не использует** ответ сервера. Вместо этого он возвращает содержимое локального ресурса `Resources.ch_list`.
4.  Этот локальный скрипт содержит большое количество вызовов `window.external.ИмяМетода()`, которые обращаются к нативному коду C# для получения дополнительной информации о контактах и формирования кнопок действий.
5.  Скрипт выполняется на странице `ch.php`, которая уже содержит глобальный JavaScript-массив `ChatListU` со списком персонажей. Скрипт использует этот массив для построения списка.

## 3. План портирования и реализации

### Шаг 1: Замена серверного скрипта локальным

-   `[x]` **Действие:** Изменена логику в `shouldInterceptRequest` в `MainActivity.java`.
-   **Задача:** При перехвате запроса на `ch/ch_list.js`:
    1.  Полностью игнорировать ответ от сервера.
    2.  Загрузить содержимое файла `ABClient/ch_list.js` (который является точной копией `Resources.ch_list` из ПК-версии) в строку.
    3.  Выполнить замену: `jsContent = jsContent.replace("alt=", "title=");`
    4.  Добавить в начало скрипта мост для `window.external`:
        ```java
        String bridgeScript = "window.external = window.AndroidBridge;\n";
        jsContent = bridgeScript + jsContent;
        ```
    5.  Вернуть `WebResourceResponse` с этим новым, полностью локально сформированным скриптом.

### Шаг 2: Удаление избыточной логики

-   `[x]` **Действие:** Очищен `shouldInterceptRequest` в `MainActivity.java`.
-   **Задача:**
    1.  Полностью удален блок кода, который обрабатывает URL `ch.php?lo=1` и пытается извлечь `ChatListU` в `AppVars.chatListU`.
    2.  Удалена глобальная переменная `AppVars.chatListU`.

### Шаг 3: Реализация методов `WebAppInterface`

-   `[x]` **Действие:** Реализованы недостающие методы в `ru.neverlands.abclient.bridge.WebAppInterface.java`.
-   **Задача:** Проанализирован `ch_list.js` и реализованы все функции, которые он вызывает через `window.external`. Каждый метод был аннотирован `@JavascriptInterface`.

**Список реализованных методов-заглушек:**

*   `[x]` `GetClassIdOfContact(String login)`: Реализован.
*   `[x]` `CheckQuick(String login, String wmlabQ)`: Реализован.
*   `[x]` `CheckFastAttack(String login, String wmlabFA)`: Реализован.
*   `[x]` `CheckFastAttackBlood(String login, String wmlabFAB)`: Реализован.
*   `[x]` `CheckFastAttackUltimate(String login, String wmlabFAU)`: Реализован.
*   `[x]` `CheckFastAttackClosedUltimate(String login, String wmlabFACU)`: Реализован.
*   `[x]` `CheckFastAttackFist(String login, String wmlabFAF)`: Реализован.
*   `[x]` `CheckFastAttackClosedFist(String login, String wmlabFACF)`: Реализован.
*   `[x]` `CheckFastAttackPortal(String login, String wmlabFP)`: Реализован.
*   `[x]` `CheckFastAttackClosed(String login, String wmlabFC)`: Реализован.
*   `[x]` `CheckFastAttackPoison(String login, String wmlabFAP)`: Реализован.
*   `[x]` `CheckFastAttackStrong(String login, String wmlabFAS)`: Реализован.
*   `[x]` `CheckFastAttackNevid(String login, String wmlabFAN)`: Реализован.
*   `[x]` `CheckFastAttackFog(String login, String wmlabFAFG)`: Реализован.
*   `[x]` `CheckFastAttackZas(String login, String wmlabFAZ)`: Реализован.
*   `[x]` `CheckFastAttackTotem(String login, String wmlabFTOT)`: Реализован.
*   `[x]` `Quick(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttack(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackBlood(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackUltimate(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackClosedUltimate(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackFist(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackClosedFist(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackPortal(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackClosed(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackPoison(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackStrong(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackNevid(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackFog(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackZas(String login)`: Добавлена заглушка с Toast.
*   `[x]` `FastAttackTotem(String login)`: Добавлена заглушка с Toast.
