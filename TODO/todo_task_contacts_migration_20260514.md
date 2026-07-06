# Задачи: полная миграция контактов app2 -> ANClient

## Рабочий список

- [x] Сверить app2 `Contact.java`, `ContactsManager.java`, `ApiRepository.java` с ANClient `Contact.cs`, `ContactsManager.cs`, `NeverApi.cs`.
- [x] Найти существующий контур ANClient и подтвердить, что исправлять нужно его, а не создавать параллельный менеджер.
- [x] Расширить `UserInfo` app2-полями из `info.cgi`.
- [x] Расширить `Contact` app2-полями, сохранить старые свойства как совместимый фасад.
- [x] Перенести effect helper/parser (`effectIds`, `effectStates`).
- [x] Расширить `NeverApi`: публичный `GetPlayerId`, `GetContactByNick`, `GetContactByPlayerId`, общий parser.
- [x] Обновить profile XML load/save новыми атрибутами с fallback для старых профилей.
- [x] Обновить `ContactsManager.Add` на полноценный lookup/parser с fallback.
- [x] Добавить refresh contact/group/neutral/all с задержкой и сохранением локальных полей.
- [x] Добавить UI details snapshot и context/toolbar refresh hooks.
- [x] Собрать `ANClient` и проверить BOM/mojibake/diff.
- [x] Вынести общий парсинг состава клана из `FormAddClan`/`FormCompas` в `NeverApi.GetClanRosterByNick`.
- [x] Заменить нерабочий `allnl.ru` на `service.neverlands.ru/info/clans.txt`, как в `app2`.
- [x] Реализовать parser формата `clans.txt`: строка клана -> список `playerId,nick,level,status#...`.
- [x] Добавить `ContactsManager.ImportClan` для импорта всех участников клана через текущий contacts pipeline.
- [x] Переделать `ContactsManager.ImportClan` на двухфазную схему: быстрое добавление snapshot из `clans.txt`, затем фоновый `RefreshContacts` через существующую очередь.
- [x] Добавить отображение эффектов персонажа после ника в списке комнаты: `ch_list.js` получает HTML через `window.external.GetEffectHtmlOfContact(...)`, с иконками и временем истечения из `effectStates`.
- [x] Для меню контактов/групп брать клан из текущего `Contact`, как в `app2`, не через повторный lookup по нику.
- [x] Подключить действие `Добавить всех из 'Название Клана'` в меню контакта/группы.
- [x] Переключить кнопку `Весь Клан` на вкладках информации игроков на прямой импорт в контакты.

## Важные решения

- Не создавать новый `ContactsRepository` рядом: это было бы дублированием существующего `ContactsManager`.
- `getid.cgi`, `info.cgi`, `logs.fcg` уже исключены из game-action queue в `ProxyRequestQueue`; дополнительно задержку refresh держим в `ContactsManager`, как app2 `updateContactsRecursive`.
- Локальные поля `ClassId`, `ToolId`, `Comments`, `Tracing` не должны затираться данными сервера.
- Cookie values не логировать; в логах допустимы URL, playerID, nick и длина cookie/header без содержимого.
- Клановый импорт не создаёт новый менеджер: `NeverApi` отвечает за roster, `ContactsManager` отвечает за добавление/обновление и TreeView.
- Активный источник состава клана должен совпадать с `app2`: `http://service.neverlands.ru/info/clans.txt`; `allnl.ru` не использовать.
- Клановый импорт не должен делать `GetAll` по каждому участнику до добавления списка: первичная фаза использует `playerId,nick,level,status` из `clans.txt`, а полная проверка выполняется после добавления через `ImportClan.PostRefresh`.
- Причина отсутствия эффектов после ника была не в parser: `NeverApi` уже сохранял `effectStates`, но `ch_list.js` не вызывал bridge для time-aware HTML эффектов.
