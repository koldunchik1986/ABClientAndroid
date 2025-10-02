# План реализации управления контактами

## 1. Анализ функциональности в ПК C# версии

Управление контактами в ПК-версии реализовано через специальную форму `FormMainContacts.cs` и несколько вспомогательных классов.

### Ключевые компоненты:
-   **`Contact.cs`**: Простой класс-модель, содержащий `Name` (string) и `ClassId` (int), где 0 - нейтрал, 1 - враг, 2 - друг.
-   **`UserConfigVars.cs`**: Определяет, что контакты хранятся в `SortedDictionary<string, Contact> Contacts` внутри `UserConfig`.
-   **`UserConfigSave.cs` / `UserConfigLoad.cs`**: Реализуют сериализацию и десериализацию словаря `Contacts` в/из XML-файла профиля (`<contacts><contactentry .../></contacts>`).
-   **`ContactsManager.cs`**: Статический хелпер с единственным методом `GetClassIdOfContact(string name)`, который по имени получает `ClassId` из `AppVars.UserConfig.Contacts`. Используется в `RoomManager` и `ScriptManager` для цветовой дифференциации ников.
-   **`FormMainContacts.cs`**: Основной UI для пользователя. Позволяет:
    -   Просматривать список всех контактов.
    -   Добавлять новый контакт, указывая ник и тип (враг/друг/нейтрал).
    -   Удалять выбранный контакт.
    -   Все изменения (добавление/удаление) происходят напрямую в статическом объекте `AppVars.UserConfig.Contacts`. Сохранение на диск происходит позже, при общем сохранении профиля.

### Вывод
В Android-версии уже корректно реализована модель `Contact.java`, хранение в `UserConfig.java` и сериализация в XML. Также присутствует `ContactsManager.getClassIdOfContact`. **Полностью отсутствует UI для управления списком контактов.**

## 2. Предлагаемое решение для Android

Необходимо создать новый экран (`Activity`) для управления контактами, который будет повторять функционал `FormMainContacts.cs`.

### Новая активность: `ContactsActivity.java`
-   **Запуск:** Добавить новый пункт меню в `MainActivity.java` (в навигационной панели), который будет запускать `ContactsActivity`.
-   **UI (`activity_contacts.xml`):**
    -   `RecyclerView` для отображения списка контактов.
    -   `EditText` для ввода ника нового контакта.
    -   `RadioGroup` с `RadioButton`'ами ("Друг", "Враг", "Нейтрал") для выбора типа.
    -   `Button` "Добавить" для добавления/обновления контакта.
    -   В каждом элементе `RecyclerView` должна быть кнопка "Удалить".
-   **Адаптер для `RecyclerView`:** Создать кастомный `RecyclerView.Adapter`, который будет принимать `List<Contact>` и отображать имя и тип контакта, а также обрабатывать нажатие на кнопку "Удалить".

### Логика работы:
1.  При создании `ContactsActivity` загружает `List<Contact>` из `AppVars.Profile.contacts.values()` и передает его в адаптер.
2.  **Добавление:** Кнопка "Добавить" считывает данные из `EditText` и `RadioGroup`, создает новый объект `Contact`, добавляет/обновляет его в `AppVars.Profile.contacts`, а затем обновляет `RecyclerView`.
3.  **Удаление:** Кнопка "Удалить" в элементе списка удаляет соответствующий контакт из `AppVars.Profile.contacts` и обновляет `RecyclerView`.
4.  **Сохранение:** При закрытии `ContactsActivity` (в `onPause` или `onDestroy`), необходимо вызвать `AppVars.Profile.save(this);`, чтобы сохранить все изменения в XML-файл профиля.

## 3. План реализации

-   [ ] **Задача 1: Создание UI для `ContactsActivity`**
    -   [ ] Создать layout-файл `activity_contacts.xml`.
    -   [ ] Добавить `RecyclerView` с id `contactsRecyclerView`.
    -   [ ] Добавить `EditText` с id `nickEditText`.
    -   [ ] Добавить `RadioGroup` с id `contactTypeRadioGroup` и тремя `RadioButton`.
    -   [ ] Добавить `Button` с id `addContactButton`.
    -   [ ] Создать layout-файл для одного элемента списка, `list_item_contact.xml`, содержащий `TextView` для имени и `ImageView` (или `Button`) для удаления.

-   [ ] **Задача 2: Создание `ContactsAdapter`**
    -   [ ] Создать класс `ContactsAdapter`, наследуемый от `RecyclerView.Adapter`.
    -   [ ] Реализовать `onCreateViewHolder`, `onBindViewHolder`, `getItemCount`.
    -   [ ] В `onBindViewHolder` настроить отображение имени и типа контакта, а также установить слушатель кликов на кнопку удаления.
    -   [ ] Реализовать интерфейс-callback для обработки удаления, который будет вызываться в `Activity`.

-   [ ] **Задача 3: Реализация логики `ContactsActivity.java`**
    -   [ ] Создать класс `ContactsActivity.java`.
    -   [ ] В `onCreate` установить `setContentView`, найти все View-элементы.
    -   [ ] Инициализировать `RecyclerView` с `LinearLayoutManager` и `ContactsAdapter`.
    -   [ ] Загрузить контакты из `AppVars.Profile.contacts` и передать их в адаптер.
    -   [ ] Реализовать обработчик для `addContactButton`, который добавляет контакт в `AppVars.Profile.contacts` и обновляет адаптер.
    -   [ ] Реализовать обработчик для callback'а удаления из адаптера, который удаляет контакт из `AppVars.Profile.contacts` и обновляет адаптер.
    -   [ ] В `onPause()` или `onDestroy()` добавить вызов `AppVars.Profile.save(this);`.

-   [ ] **Задача 4: Интеграция**
    -   [ ] Добавить новый пункт меню "Контакты" в `res/menu/activity_main_drawer.xml`.
    -   [ ] В `MainActivity.onNavigationItemSelected` добавить обработку нового пункта меню для запуска `ContactsActivity` через `Intent`.

-   [ ] **Задача 5: Доработка `ContactsManager` (если необходимо)**
    -   [ ] Проверить, нужны ли в `ContactsManager` методы для добавления/удаления, или вся логика останется в `ContactsActivity`. (Текущий анализ показывает, что логика может остаться в `Activity`).
