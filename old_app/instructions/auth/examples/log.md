# Пример лога авторизации

```
[2025-08-23 12:00:01] GET http://neverlands.ru/
COOKIES (sent):

COOKIES (all):
watermark=abc123
SET-COOKIE:
watermark=abc123; path=/; domain=neverlands.ru

[2025-08-23 12:00:02] POST http://neverlands.ru/game.php
PARAMS: player_nick=Vasya, player_password=***
COOKIES (sent):
watermark=abc123
COOKIES (all):
watermark=abc123; PHPSESSID=xyz789
SET-COOKIE:
PHPSESSID=xyz789; path=/; domain=neverlands.ru

[2025-08-23 12:00:03] GET http://neverlands.ru/main.php
COOKIES (sent):
watermark=abc123; PHPSESSID=xyz789
COOKIES (all):
watermark=abc123; PHPSESSID=xyz789
RESPONSE (decoded):
<html>...</html>
