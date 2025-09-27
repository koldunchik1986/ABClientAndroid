# Документация по исправлению утечки памяти и кэширования

## Введение

В приложении существовали две критические, взаимосвязанные проблемы:
1.  **Не работал кэш изображений:** Картинки в игре не отображались, а папка `abcache` оставалась пустой.
2.  **Происходила утечка памяти:** Приложение падало с ошибкой `OutOfMemoryError` при повторном открытии игрового экрана (`MainActivity`).

Обе проблемы были вызваны одной и той же фундаментальной ошибкой в архитектуре — попыткой переписать существующую логику вместо ее использования. Этот документ описывает первопричины и финальное, успешное решение.

## Часть 1: Проблема кэширования

### Симптом

-   Изображения в `WebView` (например, иконки предметов) не загружались.
-   Папка `Android/data/ru.neverlands.abclient/files/abcache/` всегда была пустой.

### Причина: Конфликт двух механизмов обработки трафика

В приложении существует `ProxyService`, который предназначен для обработки всего сетевого трафика `WebView`. Именно этот сервис использует класс `proxy/Cache.java` для сохранения и отдачи файлов из кэша.

Моя предыдущая реализация в `MainActivity` использовала собственный `WebViewClient` с переопределенным методом `shouldInterceptRequest`. Этот метод **перехватывал все сетевые запросы до того, как они могли дойти до `ProxyService`**. В результате, `ProxyService` и вся логика кэширования просто никогда не срабатывали.

### Решение

1.  **Исправление пути в `proxy/Cache.java`:** Первым шагом было исправление пути к папке кэша, чтобы он указывал на внешнее хранилище. Это было необходимым, но недостаточным условием.
    ```java
    // Было:
    private static final File cacheDir = new File(ABClientApplication.getAppContext().getFilesDir(), "abcache");
    // Стало:
    private static final File cacheDir = new File(ABClientApplication.getAppContext().getExternalFilesDir(null), "abcache");
    ```
2.  **Отказ от `shouldInterceptRequest`:** Ключевым исправлением стало **полное удаление** переопределенного метода `shouldInterceptRequest` из `MainActivity`. Это позволило `WebView` отправлять весь трафик через настроенный для него `ProxyService`, как и было задумано в оригинальной архитектуре приложения. `ProxyService` начал получать запросы, и его внутренняя логика кэширования заработала штатно.

## Часть 2: Проблема утечки памяти

### Симптом

-   Приложение падало с ошибкой `OutOfMemoryError` при втором или третьем переходе с `LogsActivity` (или другого экрана) обратно на `MainActivity`.
-   Анализ логов показывал, что сборщик мусора не мог освободить память, занятую старой копией `MainActivity`.

### Причина: Статическая ссылка на `WebView`

Корень проблемы лежал в глобальном классе `AppVars`:

```java
// AppVars.java
public static WeakReference<android.webkit.WebView> MainWebView;
```

В `MainActivity.onCreate` в эту переменную сохранялась ссылка на `WebView`. Хотя использовалась `WeakReference` (которая *должна* позволять сборщику мусора удалять объект), классы в фоновом потоке (`FishAjaxPhp`, `RouletteAjaxPhp`, работающие внутри `ProxyService`) обращались к этой статической переменной, чтобы напрямую вызывать методы `WebView` или связанные с ним компоненты.

Это создавало порочную связь: `ProxyService` (живущий долго) -> `AppVars` -> `MainWebView` -> `MainActivity`. Эта цепочка не давала сборщику мусора уничтожить экземпляр `MainActivity`, когда пользователь уходил с экрана. При возвращении создавался новый экземпляр, а старый оставался в памяти. После нескольких таких циклов память заканчивалась.

### Решение: Разрыв связи через `LocalBroadcastManager`

Вместо прямого обращения к `WebView` была внедрена система событий, полностью разрывающая связь между фоновыми сервисами и UI.

1.  **Удаление статической ссылки:** Переменная `MainWebView` была полностью удалена из `AppVars.java`.

2.  **Отправка событий:** Классы `FishAjaxPhp` и `RouletteAjaxPhp` теперь не пытаются найти `WebView`. Вместо этого они создают и отправляют `Intent` с определенным `Action` через `LocalBroadcastManager`.
    ```java
    // RouletteAjaxPhp.java
    Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
    intent.putExtra("url", "http://www.neverlands.ru/main.php?mselect=15");
    LocalBroadcastManager.getInstance(ABClientApplication.getAppContext()).sendBroadcast(intent);
    ```

3.  **Прием событий:** В `MainActivity` был создан `BroadcastReceiver`, который "слушает" эти `Intent`'ы.
    -   Ресивер регистрируется в `onResume()` и отменяет регистрацию в `onPause()`. Это гарантирует, что он работает только когда Activity активна, и предотвращает утечки.
    -   В методе `onReceive` ресивер проверяет `Action` и безопасно выполняет нужные действия в UI-потоке.
    ```java
    // MainActivity.java
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // ...
            if (action.equals(AppVars.ACTION_WEBVIEW_LOAD_URL)) {
                String url = intent.getStringExtra("url");
                if (url != null) {
                    binding.appBarMain.contentMain.webView.loadUrl(url);
                }
            }
        }
    };
    ```
4.  **Усиление `onDestroy`:** В качестве дополнительной меры безопасности, метод `onDestroy` в `MainActivity` был переписан для более агрессивной и надежной очистки всех `WebView`.

## Ключевые выводы

1.  **Не следует дублировать функциональность.** Если в приложении уже есть механизм (например, `ProxyService` для кэширования), нужно использовать и чинить его, а не писать свой собственный сбоку.
2.  **Статические ссылки на компоненты UI (`Context`, `View`, `Activity`) — почти всегда приводят к утечкам памяти.** Их следует избегать любой ценой.
3.  **Для связи между фоновыми потоками/сервисами и UI** необходимо использовать архитектурные паттерны, которые не создают прямых ссылок: `LocalBroadcastManager`, `LiveData`, `EventBus` и т.д.
