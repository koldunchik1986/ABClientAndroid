# План портирования ExtendedWebBrowser.cs

Файл `ExtendedWebBrowser.cs` — это ключевой компонент UI, который расширяет стандартный `WebBrowser` для получения полного контроля над навигацией и всплывающими окнами.

## Функциональность в C#

Стандартный `WebBrowser` в WinForms имеет ограниченные возможности по управлению. `ExtendedWebBrowser` решает эту проблему, используя низкоуровневые COM-интерфейсы для перехвата двух критически важных событий:

1.  **`BeforeNavigate2`**: Перехватывает навигацию **до** ее начала. Это позволяет коду проанализировать URL и решить, разрешить переход, полностью заблокировать его или обработать как-то иначе (например, как внутреннюю команду приложения).
2.  **`NewWindow3`**: Перехватывает попытки страницы открыть новое окно (через `window.open()` или `target="_blank"`). Это позволяет заблокировать открытие нового окна системного браузера и вместо этого, например, создать новую вкладку внутри самого приложения.

Затем эти низкоуровневые события транслируются в удобные C#-события `BeforeNavigate` и `BeforeNewWindow`, на которые подписывается главная форма приложения.

## Решение для портирования на Android

**Портировать этот класс не нужно.** Вся его функциональность реализуется стандартными и гораздо более простыми средствами `WebView`.

Для управления `WebView` используются два вспомогательных класса:

*   **`WebViewClient`**: Отвечает за все, что связано с контентом страницы: перехват навигации, загрузка ресурсов, обработка ошибок.
*   **`WebChromeClient`**: Отвечает за все, что связано с UI самого "браузера": обработка JS-диалогов (`alert`, `confirm`), иконки сайта (favicon), прогресс загрузки и, что самое важное, **обработка открытия новых окон**.

## План реализации (вместо портирования)

Необходимо создать кастомные реализации этих двух классов и назначить их нашему `WebView`.

```java
MyWebViewClient myWebViewClient = new MyWebViewClient();
MyWebChromeClient myWebChromeClient = new MyWebChromeClient();
myWebView.setWebViewClient(myWebViewClient);
myWebView.setWebChromeClient(myWebChromeClient);
```

1.  **Реализация аналога `BeforeNavigate` в `MyWebViewClient.java`**:
    ```java
    public class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            // Здесь будет логика, аналогичная FormMainGameBeforeNavigate.cs
            if (isGameUrl(url)) {
                return false; // Разрешить WebView загрузить URL
            } else {
                // Открыть ссылку в системном браузере
                Intent intent = new Intent(Intent.ACTION_VIEW, url);
                context.startActivity(intent);
                return true; // Сообщить WebView, что мы обработали URL сами
            }
        }
    }
    ```

2.  **Реализация аналога `BeforeNewWindow` в `MyWebChromeClient.java`**:
    ```java
    public class MyWebChromeClient extends WebChromeClient {
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            // Здесь будет логика обработки нового окна.
            // Например, создание новой вкладки в приложении.
            
            // 1. Создаем новый WebView для новой вкладки.
            WebView newWebView = new WebView(view.getContext());
            // ... (настраиваем его)
            
            // 2. Добавляем его в наш Tab-интерфейс.
            // myTabManager.addTab(newWebView);
            
            // 3. Сообщаем системе, что мы сами создали WebView для этого окна.
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(newWebView);
            resultMsg.sendToTarget();
            
            return true; // Мы обработали открытие окна.
        }
    }
    ```
