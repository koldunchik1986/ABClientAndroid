# ProfileManager (структура профиля)

**Что это?**
- Это специальный "менеджер", который хранит все профили игроков.

**Что хранит профиль?**
- Логин (имя игрока)
- Пароль
- Автоматический вход (autologin)
- Использовать прокси (useProxy)
- Автоочистка cookies (autoClearCookies)

**Пример:**
```
ProfileManager.Profile(
    login = "Vasya",
    password = "12345",
    autologin = true,
    useProxy = false,
    autoClearCookies = true
)
```

**Зачем нужно?**
- Чтобы можно было быстро выбрать нужного игрока и получить все его настройки.
