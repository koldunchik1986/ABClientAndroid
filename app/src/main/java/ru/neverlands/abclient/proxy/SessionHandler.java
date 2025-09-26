package ru.neverlands.abclient.proxy;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.neverlands.abclient.postfilter.Filter;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Обработчик сессии прокси-сервера.
 * Аналог Session.cs в оригинальном приложении.
 */
public class SessionHandler implements Runnable {
    private static final String TAG = "SessionHandler";
    private final Socket clientSocket;
    private Socket serverSocket;
    
    private String host;
    private int port;
    private String method;
    private String path;
    private String httpVersion;
    private Map<String, String> requestHeaders;
    private byte[] requestBody;
    
    private int responseCode;
    private String responseStatus;
    private Map<String, String> responseHeaders;
    private byte[] responseBody;
    
    private boolean isCustomFilter;
    private boolean isCache;
    private boolean isGameHost;
    
    /**
     * Конструктор
     * @param clientSocket сокет клиента
     */
    public SessionHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }
    
    @Override
    public void run() {
        try {
            // Чтение запроса от клиента
            if (!readRequest()) {
                closeConnection();
                return;
            }
            
            // Предварительная обработка запроса
            executeBasicRequestManipulations();
            
            // Предобработка запроса фильтром
            if (requestBody != null) {
                requestBody = Filter.preProcess("http://" + host + path, requestBody);
            }
            
            // Обработка специальных URL
            if (handleSpecialUrls()) {
                return;
            }
            
            // Установка прокси-авторизации, если необходимо
            if (AppVars.Profile != null && AppVars.Profile.DoProxy && 
                !AppVars.Profile.ProxyUserName.isEmpty() && !AppVars.Profile.ProxyPassword.isEmpty()) {
                String auth = AppVars.Profile.ProxyUserName + ":" + AppVars.Profile.ProxyPassword;
                String encodedAuth = android.util.Base64.encodeToString(auth.getBytes(), android.util.Base64.NO_WRAP);
                requestHeaders.put("Proxy-Authorization", "Basic " + encodedAuth);
            }
            
            // Удаление заголовков If-Modified-Since и If-None-Match для кастомных фильтров
            if (isCustomFilter) {
                requestHeaders.remove("If-Modified-Since");
                requestHeaders.remove("If-None-Match");
            } else if (requestHeaders.containsKey("If-Modified-Since") || requestHeaders.containsKey("If-None-Match")) {
                // Возвращаем 304 Not Modified для обычных запросов с условными заголовками
                sendNotModifiedResponse();
                return;
            }
            
            // Удаление куки из запроса и установка сохраненных куки
            requestHeaders.remove("Cookie");
            String cookieData = CookiesManager.obtain(host);
            if (cookieData != null && !cookieData.isEmpty()) {
                requestHeaders.put("Cookie", cookieData);
            }
            
            // Проверка кэша
            if (isCache) {
                byte[] cachedData = Cache.get(host + path, AppVars.CacheRefresh);
                if (cachedData != null) {
                    // Отправка данных из кэша
                    if (isCustomFilter) {
                        cachedData = Filter.process("http://" + host + path, cachedData);
                    }
                    
                    sendCachedResponse(cachedData);
                    return;
                }
            }
            
            // Отправка запроса на сервер
            sendRequestToServer();
            
            // Обработка ответа от сервера
            if (responseBody != null) {
                // Сохранение в кэш
                if (isCache && responseCode == 200) {
                    Cache.store(host + path, responseBody, isGameHost);
                }
                
                // Обработка ответа фильтром
                if (isCustomFilter && responseCode == 200) {
                    responseBody = Filter.process("http://" + host + path, responseBody);
                }
            }
            
            // Отправка ответа клиенту
            sendResponseToClient();
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling session", e);
        } finally {
            closeConnection();
        }
    }
    
    /**
     * Чтение запроса от клиента
     * @return true, если запрос успешно прочитан
     */
    private boolean readRequest() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            // Чтение первой строки запроса
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return false;
            }
            
            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                return false;
            }
            
            method = parts[0];
            String url = parts[1];
            httpVersion = parts[2];
            
            // Парсинг URL
            if (url.startsWith("http://")) {
                URL parsedUrl = new URL(url);
                host = parsedUrl.getHost();
                port = parsedUrl.getPort() != -1 ? parsedUrl.getPort() : 80;
                path = parsedUrl.getPath();
                if (parsedUrl.getQuery() != null) {
                    path += "?" + parsedUrl.getQuery();
                }
            } else {
                // Относительный URL
                path = url;
            }
            
            // Чтение заголовков
            requestHeaders = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String name = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    requestHeaders.put(name, value);
                    
                    // Извлечение хоста из заголовка Host, если он не был определен
                    if (host == null && name.equalsIgnoreCase("Host")) {
                        host = value;
                        port = 80;
                        
                        // Проверка на порт в заголовке Host
                        int colonPort = host.indexOf(':');
                        if (colonPort > 0) {
                            port = Integer.parseInt(host.substring(colonPort + 1));
                            host = host.substring(0, colonPort);
                        }
                    }
                }
            }
            
            // Чтение тела запроса, если есть
            if (requestHeaders.containsKey("Content-Length")) {
                int contentLength = Integer.parseInt(requestHeaders.get("Content-Length"));
                if (contentLength > 0) {
                    requestBody = new byte[contentLength];
                    int bytesRead = 0;
                    InputStream inputStream = clientSocket.getInputStream();
                    
                    while (bytesRead < contentLength) {
                        int read = inputStream.read(requestBody, bytesRead, contentLength - bytesRead);
                        if (read == -1) {
                            break;
                        }
                        bytesRead += read;
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error reading request", e);
            return false;
        }
    }
    
    /**
     * Базовая обработка запроса
     */
    private void executeBasicRequestManipulations() {
        // Определение, является ли хост игровым
        if (host != null && (host.equals("neverlands.ru") || host.endsWith(".neverlands.ru"))) {
            isGameHost = true;
        }
        
        // Определение, нужно ли кэшировать и фильтровать
        String url = host + path;
        if (url.endsWith(".gif") || url.endsWith(".jpg") || url.endsWith(".jpeg") || 
            url.endsWith(".png") || url.endsWith(".swf") || url.endsWith(".ico")) {
            isCache = true;
        } else if (url.endsWith(".css")) {
            isCache = true;
        } else if (url.contains(".js")) {
            isCache = true;
        }
        
        if (isGameHost && !isCache) {
            isCustomFilter = true;
        }
    }
    
    /**
     * Обработка специальных URL
     * @return true, если URL был обработан
     */
    private boolean handleSpecialUrls() {
        String url = host + path;
        
        // Обработка счетчиков и других ненужных ресурсов
        if (url.startsWith("www.neverlands.ru/cgi-bin/go.cgi?uid=") ||
            url.contains("top.list.ru") || url.contains("counter.yadro.ru")) {
            sendNotModifiedResponse();
            return true;
        }
        
        return false;
    }
    
    /**
     * Отправка запроса на сервер
     */
    private void sendRequestToServer() {
        try {
            URL url = new URL("http://" + host + path);
            
            // Создание соединения
            HttpURLConnection connection;
            if (AppVars.Profile != null && AppVars.Profile.DoProxy) {
                Proxy proxy = ProxyService.getUpstreamProxy();
                connection = (HttpURLConnection) url.openConnection(proxy);
            } else {
                connection = (HttpURLConnection) url.openConnection();
            }
            
            // Настройка соединения
            connection.setRequestMethod(method);
            connection.setDoInput(true);
            if (requestBody != null) {
                connection.setDoOutput(true);
            }
            
            // Установка заголовков
            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                // Пропускаем заголовки, которые HttpURLConnection устанавливает автоматически
                if (!entry.getKey().equalsIgnoreCase("Content-Length") && 
                    !entry.getKey().equalsIgnoreCase("Host")) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            
            // Отправка тела запроса
            if (requestBody != null) {
                connection.setRequestProperty("Content-Length", String.valueOf(requestBody.length));
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody);
                }
            }
            
            // Получение ответа
            responseCode = connection.getResponseCode();
            responseStatus = connection.getResponseMessage();
            
            // Чтение заголовков ответа
            responseHeaders = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                if (entry.getKey() != null) {
                    responseHeaders.put(entry.getKey(), String.join(", ", entry.getValue()));
                }
            }
            
            // Обработка куки
            List<String> cookies = connection.getHeaderFields().get("Set-Cookie");
            if (cookies != null) {
                for (String cookie : cookies) {
                    CookiesManager.assign(host, cookie);
                }
                responseHeaders.remove("Set-Cookie");
            }
            
            // Чтение тела ответа
            try (InputStream is = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (is != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    responseBody = baos.toByteArray();
                }
            }
            
            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error sending request to server", e);
            responseCode = 500;
            responseStatus = "Internal Server Error";
            responseHeaders = new HashMap<>();
            responseBody = ("Error: " + e.getMessage()).getBytes();
        }
    }
    
    /**
     * Отправка ответа клиенту
     */
    private void sendResponseToClient() {
        try {
            OutputStream os = clientSocket.getOutputStream();
            
            // Отправка статусной строки
            String statusLine = httpVersion + " " + responseCode + " " + responseStatus + "\r\n";
            os.write(statusLine.getBytes());
            
            // Отправка заголовков
            for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
                String headerLine = entry.getKey() + ": " + entry.getValue() + "\r\n";
                os.write(headerLine.getBytes());
            }
            
            // Отправка Content-Length
            if (responseBody != null) {
                String contentLength = "Content-Length: " + responseBody.length + "\r\n";
                os.write(contentLength.getBytes());
            }
            
            // Пустая строка, отделяющая заголовки от тела
            os.write("\r\n".getBytes());
            
            // Отправка тела
            if (responseBody != null) {
                os.write(responseBody);
            }
            
            os.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error sending response to client", e);
        }
    }
    
    /**
     * Отправка ответа 304 Not Modified
     */
    private void sendNotModifiedResponse() {
        try {
            OutputStream os = clientSocket.getOutputStream();
            String response = httpVersion + " 304 Not Modified\r\nServer: Cache\r\n\r\n";
            os.write(response.getBytes());
            os.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error sending 304 response", e);
        }
    }
    
    /**
     * Отправка кэшированного ответа
     * @param cachedData данные из кэша
     */
    private void sendCachedResponse(byte[] cachedData) {
        try {
            OutputStream os = clientSocket.getOutputStream();
            String response = httpVersion + " 200 OK\r\nServer: Cache\r\nContent-Length: " + cachedData.length + "\r\n\r\n";
            os.write(response.getBytes());
            os.write(cachedData);
            os.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error sending cached response", e);
        }
    }
    
    /**
     * Закрытие соединений
     */
    private void closeConnection() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing connections", e);
        }
    }
}
