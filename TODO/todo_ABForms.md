# Общий анализ папки ABForms

Папка `ABForms` содержит исходный код для всех окон (форм) пользовательского интерфейса C#-приложения, разработанного на технологии Windows Forms.

## Структура файлов

Для каждой формы обычно существует три типа файлов:

*   **`ИмяФормы.cs`**: Основной файл с "логикой позади" (code-behind). Здесь находится код, который выполняется при взаимодействии с формой — обработчики нажатий кнопок, загрузка данных, обновление элементов и т.д.
*   **`ИмяФормы.Designer.cs`**: Автоматически сгенерированный средой Visual Studio код. Он описывает саму структуру формы: какие на ней есть панели, кнопки, метки, текстовые поля, их размеры, расположение, названия и свойства.
*   **`ИмяФормы.resx`**: Файл ресурсов. В нем хранятся изображения, иконки, строки и другие ассеты, которые используются на данной форме.

## Ключевые файлы

Судя по названиям, самым важным файлом в этой папке является `FormMain.cs` (и все связанные с ним `FormMain...cs` файлы). Это, очевидно, главное окно приложения, в котором происходит вся основная игровая активность.

Остальные файлы — это вспомогательные диалоговые окна (настройки, сообщения об ошибках, компас и т.д.).

## Сложность портирования

Портирование пользовательского интерфейса с Windows Forms на Android — **это не перенос кода 1-в-1, а полное воссоздание с нуля.**

*   **Визуальная часть (`.Designer.cs`, `.resx`)** должна быть заменена на Android-эквиваленты: XML-файлы разметки (layouts) или функции Jetpack Compose.
*   **Логика (`.cs`)** должна быть адаптирована под жизненный цикл Android-компонентов (Activity, Fragment) и его архитектурные паттерны (ViewModel, Repository). Вся прямая работа с элементами UI (`button.Text = ...`) должна быть заменена на работу с данными через `LiveData` или `StateFlow`.

## Список файлов в папке

*   ClearExplorerCacheForm.cs
*   ClearExplorerCacheForm.designer.cs
*   ClearExplorerCacheForm.resx
*   ErrorForm.cs
*   ErrorForm.designer.cs
*   ErrorForm.resx
*   FormAddClan.cs
*   FormAddClan.Designer.cs
*   FormAddClan.resx
*   FormAutoAttack.cs
*   FormAutoBait.cs
*   FormAutoBait.Designer.cs
*   FormAutoBait.resx
*   FormCompas.cs
*   FormCompas.Designer.cs
*   FormCompas.resx
*   FormMain.cs
*   FormMain.Designer.cs
*   FormMain.resx
*   FormMainAutoBoi.cs
*   FormMainBulk.cs
*   FormMainChat.cs
*   FormMainCheckInfo.cs
*   FormMainCheckTied.cs
*   FormMainContacts.cs
*   FormMainCross.cs
*   FormMainDelegates.cs
*   FormMainDocumentCompleted.cs
*   FormMainDom.cs
*   FormMainDownloadKey.cs
*   FormMainFast.cs
*   FormMainGameBeforeNavigate.cs
*   FormMainGua.cs
*   FormMainHerbs.cs
*   FormMainInfoToolTip.cs
*   FormMainInit.cs
*   FormMainMap.cs
*   FormMainNavigator.cs
*   FormMainSettings.cs
*   FormMainSize.cs
*   FormMainSmiles.cs
*   FormMainStat.cs
*   FormMainTabs.cs
*   FormMainTicks.cs
*   FormMainTimers.cs
*   FormMainTray.cs
*   FormMainWaitForTurn.cs
*   FormSettingsAutoCut.cs
*   FormSettingsAutoCut.Designer.cs
*   FormSettingsAutoCut.resx
