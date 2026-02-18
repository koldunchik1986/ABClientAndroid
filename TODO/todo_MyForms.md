# Анализ папки MyForms

В этой папке содержатся диалоговые формы (окна и диалоги) приложения.
**Источник истины**: `ABClient.csproj` — в проект включены 22 .cs файла (11 форм + 11 Designer).

## Список форм и статус анализа / реализации

**Легенда статуса реализации:**
- `[+]` — Полностью портировано
- `[~]` — Частично портировано
- `[ ]` — Не реализовано
- `[-]` — Отложено / низкий приоритет

| Форма | Описание | Анализ | Реализация | Android-аналог | Компонент |
| ----- | -------- | ------ | ---------- | -------------- | --------- |
| `FormProfile.cs` | Редактирование профиля (ник, ключ, пароль, прокси) | `[x]` | `[+]` | `ProfileActivity.java` | Activity |
| `FormProfiles.cs` | Выбор профиля из списка | `[x]` | `[+]` | `ProfilesActivity.java` | Activity |
| `FormCode.cs` | Ввод капчи (изображение + код) | `[x]` | `[+]` | `LoginActivity.showCaptchaDialog()` | AlertDialog |
| `FormAskPassword.cs` | Проверка пароля (ввод + хеш-сравнение) | `[x]` | `[+]` | `dialog_enter_password.xml` | AlertDialog |
| `FormNewPassword.cs` | Создание нового пароля (двойной ввод) | `[x]` | `[+]` | `dialog_create_password.xml` | AlertDialog |
| `FormSettingsGeneral.cs` | Основные настройки (~500 строк): чат, карта, рыбалка, звуки, торговля, бой, лечение | `[x]` | `[~]` | `SettingsActivity.java` (минимальная) | Activity + PreferenceFragment |
| `FormAbout.cs` | Окно "О программе" (лицензия) | `[x]` | `[ ]` | — | AlertDialog |
| `FormAutoLogon.cs` | Автовход с обратным отсчётом (3 сек) | `[x]` | `[ ]` | — | AlertDialog + CountDownTimer |
| `FormEnterInt.cs` | Ввод целого числа (min/max/валидация) | `[x]` | `[ ]` | — | AlertDialog + EditText |
| `FormFishAdvisor.cs` | Советник рыбака (база рыбных мест, расчёт по навыку) | `[x]` | `[ ]` | — | Activity/Fragment + RecyclerView |
| `FormGroup.cs` | Ввод имени группы | `[x]` | `[ ]` | — | AlertDialog + EditText |
| `FormNavigator.cs` | Навигатор по карте (TreeView + WebBrowser + маршруты) | `[x]` | `[ ]` | — | Activity + WebView + ExpandableListView |
| `FormNewTab.cs` | Открытие новой вкладки (URL/ID) | `[x]` | `[ ]` | — | AlertDialog + EditText + RadioGroup |
| `FormNewTimer.cs` | Создание таймера (зелье/перемещение/экипировка) | `[x]` | `[ ]` | — | Dialog/BottomSheet |
| `FormNewVersion.cs` | Уведомление о новой версии | `[x]` | `[ ]` | — | AlertDialog (URL через Intent) |
| `FormPromptExit.cs` | Подтверждение выхода с обратным отсчётом (10 сек) | `[x]` | `[ ]` | — | AlertDialog + CountDownTimer |
| `FormQuick.cs` | Панель быстрых атак (11 типов) | `[x]` | `[+]` | `QuickActionsBottomSheet.java` + `FastActionManager.java` | BottomSheet (полная цепочка атак) |
| `FormShowCookies.cs` | Отображение и копирование cookies | `[x]` | `[ ]` | — | AlertDialog + ClipboardManager |
| `FormSmiles.cs` | Выбор смайлов (2 вкладки, сетка изображений) | `[x]` | `[ ]` | — | BottomSheet + ViewPager2 + Grid |
| `FormSostav.cs` | Редактор состава группы/пати | `[x]` | `[ ]` | — | Dialog/Fragment + RecyclerView |
| `FormSostavMain.cs` | Просмотр состава группы (read-only) | `[x]` | `[ ]` | — | AlertDialog + ListView |
| `FormStatEdit.cs` | Редактирование строки статистики | `[x]` | `[ ]` | — | AlertDialog + EditText (multiline) |

## Сводная статистика

| Категория | Количество |
| --------- | ---------- |
| `[+]` Полностью портировано | 5 (FormProfile, FormProfiles, FormCode, FormAskPassword, FormNewPassword) |
| `[~]` Частично портировано | 1 (FormSettingsGeneral) |
| `[ ]` Не реализовано | 16 |
| **Итого форм** | **22** |

## Приоритеты портирования

### Высокий приоритет (базовая игра)
1. **FormSettingsGeneral.cs** — расширить SettingsActivity (сейчас минимальная). Критично для настроек чата, карты, боя.
2. **FormAutoLogon.cs** — автовход при наличии сохранённого профиля.
3. **FormPromptExit.cs** — подтверждение выхода.
4. **FormNavigator.cs** — навигатор по карте. Сложный — нужен WebView + маршруты. Зависит от ExtMap.

### Средний приоритет (удобство)
5. **FormQuick.cs** — панель быстрых атак.
6. **FormSmiles.cs** — выбор смайлов для чата.
7. **FormNewTimer.cs** — пользовательские таймеры.
8. **FormShowCookies.cs** — просмотр cookies (для отладки).
9. **FormEnterInt.cs** — утилитный диалог, используется в настройках.

### Низкий приоритет (специализированное)
10. **FormFishAdvisor.cs** — советник рыбака (зависит от системы рыбалки).
11. **FormSostav.cs / FormSostavMain.cs** — состав пати.
12. **FormNewTab.cs** — мульти-вкладки (зависит от системы вкладок).
13. **FormGroup.cs** — группы контактов.
14. **FormStatEdit.cs** — статистика.
15. **FormNewVersion.cs** — проверка версии.
16. **FormAbout.cs** — информация о программе.

## Особенности портирования

### Windows-специфичные зависимости
- **FormNavigator.cs** — использует `ExtendedWebBrowser` (IE COM), `ObjectForScripting`, `Application.DoEvents()`. Потребуется Android WebView с `@JavascriptInterface`.
- **FormNewTab.cs** — `System.Windows.Forms.Clipboard`. Замена: Android `ClipboardManager`.
- **FormNewVersion.cs** — `Process.Start(url)`. Замена: `Intent(ACTION_VIEW, Uri.parse(url))`.
- **FormShowCookies.cs** — `ExternalException` для clipboard. Замена: Android `ClipboardManager`.
- **FormSettingsGeneral.cs** — иконки трея, balloon-уведомления. Замена: Android Notification.
