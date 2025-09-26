# План портирования SerialKey.cs

Файл `SerialKey.cs` содержит логику для генерации и проверки лицензионных ключей приложения.

## Функциональность в C#

Класс содержит два независимых механизма:

1.  **`Newyork(nick, expiredDate)` (активный)**:
    *   Генерирует строку-ключ на основе ника пользователя и даты истечения. 
    *   Алгоритм использует MD5-хэширование и кастомный алфавит для формирования ключа формата `XXXX-XXXX-XXXX-XXXX`.

2.  **Система лицензий на основе Rijndael (закомментирована)**:
    *   В файле присутствует полностью закомментированный блок кода, который реализует полноценную систему лицензирования.
    *   `Encrypt`: Шифрует данные о лицензии (ник, дата, срок действия) с помощью алгоритма Rijndael (AES) и секретной фразы.
    *   `Decrypt` / `IsAllowed`: Расшифровывают и проверяют валидность лицензионного ключа.

## Решение для портирования на Android

Системы лицензирования с ручным вводом ключей практически не используются в современных мобильных приложениях. Для монетизации и управления доступом в Android используются встроенные механизмы Google Play (In-App Purchases, подписки).

**Рекомендация:** Считать всю систему лицензирования устаревшей и **не портировать** ее, если не будет явных требований по сохранению этой логики. Основное внимание следует уделить портированию метода `Newyork`, так как он не закомментирован и может использоваться в других частях приложения.

## План портирования

1.  **Создать `SerialKey.java`** в пакете `ru.neverlands.abclient.utils`.
2.  **Реализовать метод `newyork`**:
    ```java
    import java.security.MessageDigest;
    import java.text.SimpleDateFormat;
    import java.util.Date;
    import java.util.Locale;

    public static String newyork(String nick, Date expiredDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
            String dateStr = sdf.format(expiredDate);
            String str = "((++" + nick.toUpperCase() + "***" + dateStr + "++))";
            
            byte[] buffer = str.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashbuffer = md.digest(buffer);
            
            char[] m = "ОЕАИНТСРВЛКМПУЯГ".toCharArray();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                // >> 4 is equivalent to / 16, & 0xF is equivalent to % 16
                sb.append(m[(hashbuffer[i] & 0xFF) >> 4]);
                sb.append(m[hashbuffer[i] & 0x0F]);
                if (((i + 1) % 4) == 0 && (i != 15)) {
                    sb.append('-');
                }
            }
            
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    ```
3.  **Закомментированный код**: Остальную часть класса (методы `Encrypt`, `Decrypt`, `IsAllowed`) **не портировать**, но оставить комментарий в `TODO`-файле о их существовании на случай, если эта логика все же понадобится в будущем.
