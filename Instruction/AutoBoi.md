# Инструкция: Авто-Бой (Auto-Boi)

## Общее описание

Авто-Бой — система автоматического ведения боя в игре Neverlands. При включении:
1. Когда открыт фрейм боя (`main.php` с переменной `var fight_ty`) — `LezFight.parse()` анализирует HTML, генерирует оптимальную комбинацию ударов/блоков/магии
2. Результат (`Frame`) — отправляется на сервер напрямую через OkHttp
3. Цикл повторяется: парсим ответ → генерируем комбинацию → отправляем → ...

## Включение авто-боя

1. Войдите в профиль персонажа
2. Нажмите кнопку **AUTO_FIGHT** на панели быстрых кнопок (или используйте горячую клавишу)
3. Кнопка станет активной (зеленой)
4. Авто-бой включен

## Настройка авто-боя

Настройки авто-боя находятся в профиле персонажа. Основные параметры:

### Общие настройки
- **LezDoAutoboi** — включить авто-бой
- **LezDoWaitHp** — ждать восстановления HP до указанного %
- **LezWaitHp** — процент HP для ожидания (0-100)
- **LezDoWaitMa** — ждать восстановления MA до указанного %
- **LezWaitMa** — процент MA для ожидания (0-100)
- **LezDoDrinkHp** — пить зелья HP
- **LezDrinkHp** — пить если HP < %
- **LezDoDrinkMa** — пить зелья MA
- **LezDrinkMa** — пить если MA < %

### Группы противников
Можно настроить разные стратегии для разных типов противников:
- Боты (орк, гоблин и т.д.)
- Игроки
- Боссы

### Ротация (тип атак)
- **DoHits** — обычные удары
- **DoBlocks** — обычные блоки
- **DoMagHits** — магические удары
- **DoMagBlocks** — магические блоки
- **SpellsHits/SpellsBlocks** — выбор заклинаний

### Останов боя
- **DoStopNow** — остановить немедленно
- **DoStopLowHp** — остановить при низком HP
- **StopLowHp** — порог HP для остановки
- **DoStopLowMa** — остановить при низкой MA
- **DoExit** — выйти из боя

## Архитектура

### Основные классы

| Класс | Назначение |
|-------|-----------|
| `LezFight.java` | Парсинг HTML боя, генерация комбинаций ударов |
| `LezBotsGroup.java` | Настройки группы противников |
| `LezBotsClass.java` | Класс противника (бот/игрок/босс) |
| `LezSpell.java` | Заклинание |
| `LezNode.java` | Узел комбинации (удар/блок/магия) |
| `MainPhp.java` | Обработка страницы боя, цикл авто-боя |
| `WebAppInterface.java` | JavaScript интерфейс для WebView |

### Поток выполнения

```
WebView загружает main.php?get_id=56&act=10&go=inf
    ↓
MainPhp.process() вызывает mainPhpFight()
    ↓
LezFight.parse() анализирует HTML, извлекает:
    - fight_ty (тип боя, OD, MA)
    - param_en (HP противника, имя)
    - slots_en (изображение противника)
    ↓
LezFight.GenerateCombinations() генерирует комбинации:
    - _lezHits (доступные удары)
    - _lezBlocks (доступные блоки)
    - _lezMagics (доступная магия)
    ↓
Выбирается лучшая комбинация (по приоритету Z и доступному OD)
    ↓
executeFightPost() отправляет POST запрос напрямую (мимо WebView)
    ↓
Парсим ответ, проверяем:
    - Бой продолжается? → следующая итерация
    - Бой завершён? → executeFightEndPost()
    ↓
Цикл повторяется до лимита (10 атак) или показываем пользователю
```

## Отладка

### Логи

Основные теги для фильтрации:
```
adb logcat -s MainPhp:V LezFight:V
```

### Ключевые сообщения

| Сообщение | Описание |
|-----------|----------|
| `LezFight: Selected combo: LezNode{...}` | Выбрана комбинация для атаки |
| `mainPhpFight: auto attack #N executed via POST` | Атака отправлена |
| `mainPhpFight: sleep XXXms before next attack` | Задержка между атаками (анти-детект) |
| `mainPhpFight: FIGHT ENDED` | Бой завершён |
| `executeFightEndPost: using GET request` | Запрос на завершение боя |
| `FILTERED OUT: OD=XXX/200` | Комбинация отфильтрована (превышает OD) |

### Частые проблемы

#### 1. Авто-бой не делает удары
- Проверьте что `LezDoAutoboi` включен в профиле
- Проверьте логи на наличие `Selected combo` с `Hits[...]`
- Проверьте что `DoHits=true` в настройках группы

#### 2. Бой не завершается автоматически
- Проверьте наличие `BuildFightLink` в логах
- Проверьте что `executeFightEndPost` выполняется

#### 3. Сервер детектит авто-бой
- Проверьте наличие задержки между атаками (`sleep XXXms`)
- Увеличьте задержку в коде (сейчас 1000-2000ms)

## Файлы

### Java (Android)
- `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java`
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
- `app/src/main/java/ru/neverlands/abclient/bridge/WebAppInterface.java`
- `app/src/main/java/ru/neverlands/abclient/model/LezBotsGroup.java`
- `app/src/main/java/ru/neverlands/abclient/model/LezNode.java`

### C# (ПК версия, эталон)
- `ABClient/Lez/LezFight.cs`
- `ABClient/Lez/LezBotsGroup.cs`
- `ABClient/Lez/LezNode.cs`
- `ABClient/PostFilter/MainPhpFight.cs`

## История изменений

### 25.02.2026
- Добавлена задержка между атаками (1000-2000ms рандомно) для анти-детекта
- Исправлено завершение боя — используется GET запрос (как в браузере)
- Добавлен метод `executeFightEndPost()` для автоматического завершения боя

### Ранние версии
- Портирование логики авто-боя с C# на Java
- Интеграция с MainPhp
- Генерация комбинаций ударов/блоков/магии
- Обработка завершения боя
