# Общий анализ папки MyForms

Папка `MyForms`, как и `ABForms` и `Forms`, содержит большое количество UI-компонентов (окон), созданных на Windows Forms. По всей видимости, в проекте не было строгого разделения, и UI-классы создавались в разных местах.

## Содержимое папки

Здесь можно выделить несколько групп форм:

1.  **Управление профилями**: `FormProfiles`, `FormProfile`, `FormAskPassword`, `FormNewPassword`. Эти файлы дублируют или являются аналогами тех, что мы уже видели в папке `Forms`. При портировании нужно будет разобраться, какая из версий является актуальной, и создать единую систему управления профилями.
2.  **Настройки**: `FormSettingsGeneral` — окно с основными настройками приложения.
3.  **Информационные и служебные окна**: `FormAbout` (об авторе), `FormNewVersion` (о выходе новой версии), `FormCode` (для ввода капчи), `FormPromptExit` (подтверждение выхода).
4.  **Игровые инструменты**: `FormNavigator` (навигатор по карте), `FormFishAdvisor` (советник по рыбалке), `FormSmiles` (панель смайлов), `FormSostav` (просмотр состава клана).
5.  **Простые диалоги**: `FormEnterInt` (для ввода числа), `FormNewTab` (для создания новой вкладки).

## Решение для портирования на Android

**Прямое портирование этих форм невозможно.**

Весь этот функционал должен быть воссоздан с нуля с использованием нативных компонентов Android и современных архитектурных подходов.

*   **Настройки**: Вся логика настроек из `FormSettingsGeneral` и других подобных форм должна быть объединена в единый экран настроек на базе `PreferenceScreen`.
*   **Инструменты**: Каждый инструмент (`Navigator`, `FishAdvisor`) должен стать отдельным `Activity` или `Fragment` со своей `ViewModel`.
*   **Диалоги**: Все простые окна (`AskPassword`, `EnterInt`, `PromptExit`) должны быть заменены на `AlertDialog`.
*   **Дубликаты**: Необходимо будет провести ревизию и избавиться от дублирующей логики (например, если `MyForms/FormProfile` и `Forms/FormProfile` делают одно и то же, нужно будет создать только один экран настроек профиля в Android).

## Список файлов в папке

*   FormAbout.cs
*   FormAskPassword.cs
*   FormAutoLogon.cs
*   FormCode.cs
*   FormEnterInt.cs
*   FormFishAdvisor.cs
*   FormGroup.cs
*   FormNavigator.cs
*   FormNewPassword.cs
*   FormNewTab.cs
*   FormNewTimer.cs
*   FormNewVersion.cs
*   FormProfile.cs
*   FormProfiles.cs
*   FormPromptExit.cs
*   FormQuick.cs
*   FormSettingsGeneral.cs
*   FormShowCookies.cs
*   FormSmiles.cs
*   FormSostav.cs
*   FormSostavMain.cs
*   FormStatEdit.cs
*   (и связанные с ними .Designer.cs и .resx файлы)
