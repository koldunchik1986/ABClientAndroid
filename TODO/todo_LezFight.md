# План портирования Авто-Боя (LezFight) на Android

## 1. Общее описание

Авто-Бой (LezFight) - это система автоматического ведения боя в игре Neverlands. Она анализирует HTML страницу боя, генерирует оптимальные комбинации ударов/блоков/магии и автоматически выполняет ход.

### Компоненты ПК версии для портирования

| Файл | Описание | Статус в Android |
|------|----------|------------------|
| `LezFight.cs` | Основной класс парсинга боя и генерации комбинаций | ✅ Портирован (LezFight.java) |
| `LezBotsGroup.cs` | Группа ботов с настройками | ✅ Портирован (LezBotsGroup.java) |
| `LezBotsClass.cs` | Класс бота | ✅ Портирован (LezBotsClass.java) |
| `LezBotsClassCollection.cs` | Коллекция классов ботов | ✅ Портирован (LezBotsClassCollection.java) |
| `LezSpell.cs` | Заклинание | ✅ Портирован (LezSpell.java) |
| `LezSpellCollection.cs` | Коллекция заклинаний | ✅ Портирован (LezSpellCollection.java) |
| `LezNode.cs` | Узел комбинации | ✅ Портирован (LezNode.java) |
| `AutoboiState.cs` | Enum состояний автобоя | ✅ Портирован (AutoboiState.java) |
| `FormSettingsAb.cs` | Форма настроек автобоя | ❌ Не портирован |
| `FightJs.cs` | JavaScript инъекция для боя | ⚠️ Требует адаптации |
| `MainPhpFight.cs` | Обработчик страницы боя | ⚠️ Требует адаптации |

---

## 2. Переменные профиля (UserConfigVars.cs)

### Настройки автобоя в профиле пользователя

```csharp
// Lez AutoBoi - Основные настройки
internal bool LezDoAutoboi = true;           // Включен ли автобой
internal bool LezDoWaitHp = false;          // Ждать восстановления HP
internal bool LezDoWaitMa = false;          // Ждать восстановления MA
internal int LezWaitHp = 100;               // Ждать пока HP станет (%)
internal int LezWaitMa = 100;                // Ждать пока MA станет (%)
internal bool LezDoDrinkHp = false;         // Пить зелья HP
internal bool LezDoDrinkMa = true;           // Пить зелья MA
internal int LezDrinkHp = 50;                // Пить если HP < (%)
internal int LezDrinkMa = 50;                // Пить если MA < (%)
internal bool LezDoWinTimeout = true;        // Выйти из боя по таймауту
internal LezSayType LezSay = LezSayType.No; // Тип сообщения после боя
internal List<LezBotsGroup> LezGroups = new List<LezBotsGroup> { new LezBotsGroup(001, 0) };
```

### Enum LezSayType

```csharp
public enum LezSayType {
    No = 0,   // Не отправлять
    Chat = 1, // В чат
    Clan = 2, // В клан
    Pair = 3  // Приватом
}
```

---

## 3. Настройки группы ботов (LezBotsGroup)

### Переменные класса LezBotsGroup

```csharp
public class LezBotsGroup {
    // Идентификация
    public int Id;           // ID класса (001=Все, 010=Человек, 020=Бот, 021=Босс, 101+=Конкретные классы)
    public int MinimalLevel; // Минимальный уровень
    
    // Восстановление (HP/MA)
    public bool DoRestoreHp;    // Восстанавливать HP
    public bool DoRestoreMa;    // Восстанавливать MA
    public int RestoreHp;       // Восстанавливать если HP < (%)
    public int RestoreMa;       // Восстанавливать если MA < (%)
    
    // Абилки группы
    public bool DoAbilBlocks;   // Использовать абилки-блоки
    public bool DoAbilHits;     // Использовать абилки-удары
    public bool DoMagHits;      // Использовать магические удары
    public int MagHits;         // Магические удары (значение)
    public bool DoMagBlocks;    // Использовать магические блоки
    public bool DoHits;         // Использовать обычные удары
    public bool DoBlocks;       // Использовать обычные блоки
    public bool DoMiscAbils;    // Использовать прочие абилки
    
    // Останов боя
    public bool DoStopNow;      // Остановить немедленно
    public bool DoStopLowHp;    // Остановить при низком HP
    public bool DoStopLowMa;    // Остановить при низкой MA
    public int StopLowHp;       // Остановить если HP < (%)
    public int StopLowMa;       // Остановить если MA < (%)
    public bool DoExit;         // Выйти из боя
    public bool DoExitRisky;    // Выйти при опасном противнике
    
    // Списки заклинаний
    public int[] SpellsHits;      // Заклинания для ударов
    public int[] SpellsBlocks;    // Заклинания для блоков
    public int[] SpellsRestoreHp; // Заклинания восстановления HP
    public int[] SpellsRestoreMa; // Заклинания восстановления MA
    public int[] SpellsMisc;      // Прочие заклинания
}
```

---

## 4. Классы ботов (LezBotsClassCollection)

### Список классов ботов

| ID | Name | Plural |
|----|------|--------|
| 001 | Все | Все |
| 010 | Человек | Люди |
| 020 | Бот | Боты |
| 021 | Босс | Боссы |
| 101 | Орк | Орки |
| 102 | Гоблин | Гоблины |
| 103 | Крыса | Крысы |
| 104 | Кабан | Кабаны |
| 105 | Ядовитый паук | Ядовитые пауки |
| 106 | Скелет | Скелеты |
| 107 | Скелет-мечник | Скелеты-мечники |
| 108 | Зомби | Зомби |
| 109 | Тролль | Тролли |
| 110 | Огр | Огры |
| 111 | Огр-берсеркер | Огры-берсеркеры |
| 112 | Сильф | Сильфы |
| 113 | Нетопырь | Нетопыри |
| 114 | Разбойник | Разбойники |
| 115 | Грабитель | Грабители |
| 116 | Призрак | Призраки |
| 117 | Некромант | Некроманты |
| 118 | Элементаль | Элементали |
| 119 | Дварф | Дварфы |
| 120 | Медведь | Медведи |
| 121 | Воин Таэров | Воины Таэров |

---

## 5. Состояния автобоя (AutoboiState)

### Enum AutoboiState

```csharp
public enum AutoboiState {
    AutoboiOff = 0,  // Автобой выключен
    AutoboiOn = 1,   // Автобой включен и работает
    Timeout = 2,     // Ожидание (таймаут после остановки)
    Restoring = 3,   // Лечение после боя
    Guamod = 4       // Распознавание капчи
}
```

---

## 6. Форма настроек автобоя (FormSettingsAb)

### Вкладки формы

#### Вкладка 1: Общие настройки

| Элемент | Тип | Описание |
|---------|-----|----------|
| checkDoAutoboi | CheckBox | Включить автобой |
| checkDoWaitHp | CheckBox | Ждать восстановления HP |
| linkWaitHp | LinkLabel | Процент HP для ожидания |
| checkDoWaitMa | CheckBox | Ждать восстановления MA |
| linkWaitMa | LinkLabel | Процент MA для ожидания |
| checkDoDrinkHp | CheckBox | Пить зелья HP |
| linkDrinkHp | LinkLabel | Порог для зелий HP |
| checkDoDrinkMa | CheckBox | Пить зелья MA |
| linkDrinkMa | LinkLabel | Порог для зелий MA |
| checkDoWinTimeout | CheckBox | Выйти по таймауту |
| radioSayNo | RadioButton | Не отправлять сообщение |
| radioSayChat | RadioButton | Отправить в чат |
| radioSayClan | RadioButton | Отправить в клан |
| radioSayPair | RadioButton | Отправить приват |

#### Вкладка 2: Группы

| Элемент | Тип | Описание |
|---------|-----|----------|
| listGroups | ListBox | Список групп ботов |
| comboNewGroup | ComboBox | Выбор класса для новой группы |
| linkNewGroupLevel | LinkLabel | Минимальный уровень |
| buttonCreateGroup | Button | Создать группу |
| buttonDeleteGroup | Button | Удалить группу |

#### Вкладка 3: Настройки выбранной группы (Ротация)

| Элемент | Тип | Описание |
|---------|-----|----------|
| checkDoRestoreHp | CheckBox | Восстанавливать HP |
| linkRestoreHp | LinkLabel | Порог восстановления HP |
| checkDoRestoreMa | CheckBox | Восстанавливать MA |
| linkRestoreMa | LinkLabel | Порог восстановления MA |
| checkDoAbilBlocks | CheckBox | Использовать абилки-блоки |
| checkDoAbilHits | CheckBox | Использовать абилки-удары |
| checkDoMagHits | CheckBox | Использовать магические удары |
| linkMagHits | LinkLabel | Значение магических ударов |
| checkDoMagBlocks | CheckBox | Использовать магические блоки |
| checkDoHits | CheckBox | Использовать обычные удары |
| checkDoBlocks | CheckBox | Использовать обычные блоки |
| checkDoMiscAbils | CheckBox | Использовать прочие абилки |
| listSpellsHits | ListView | Список заклинаний ударов (с чекбоксами) |
| listSpellsBlocks | ListView | Список заклинаний блоков |
| listSpellsRestoreHp | ListView | Список заклинаний HP |
| listSpellsRestoreMa | ListView | Список заклинаний MA |
| listSpellsMisc | ListView | Список прочих заклинаний |
| buttonFullHp | Button | Восстановить до 100% HP |
| buttonFullMa | Button | Восстановить до 100% MA |

#### Вкладка 4: Останов боя

| Элемент | Тип | Описание |
|---------|-----|----------|
| checkDoStopNow | CheckBox | Остановить немедленно |
| checkDoStopLowHp | CheckBox | Остановить при низком HP |
| linkStopLowHp | LinkLabel | Порог остановки по HP |
| checkDoStopLowMa | CheckBox | Остановить при низкой MA |
| linkStopLowMa | LinkLabel | Порог остановки по MA |
| checkDoExit | CheckBox | Выйти из боя |
| checkDoExitRisky | CheckBox | Выйти при опасном противнике |

---

## 7. Парсинг информации о бое из HTML

### Переменные JavaScript на странице боя

| Переменная | Описание |
|------------|----------|
| `fight_ty` | Тип боя, состояние |
| `param_ow` | Параметры игрока (HP, MA, уровень) |
| `param_en` | Параметры противника |
| `slots_en` | Изображение противника |
| `fight_pm` | Параметры боя (макс мага, ОД, удар, vcode) |
| `stand_in` | Доступные удары |
| `magic_in` | Доступная магия |
| `alchemy` | Алхимия |
| `logs` | История боя |

### Информация о противнике

Из HTML можно получить:
- **Имя противника** (`FoeName`) - кто напал (бот/человек/невидимка/босс)
- **Уровень противника** (`_foeLevel`)
- **Изображение** (`_foeImage`) - определяет тип противника
- **HP противника** (`param_en[1]/param_en[2]`)
- **MA противника** (`param_en[3]/param_en[4]`)
- **Тип боя** (`_ftype`) - 0=обычный, 80+=опасный

### Типы противников

- `bot*` - бот
- `_xneto*` - нетопырь
- `_xsilf*` - сильф
- иначе - **Человек**

### Определение босса

```csharp
private bool IsBossName(string name) {
    return name.Equals("Королева Змей") ||
           name.Equals("Хранитель Леса") ||
           name.Equals("Громлех Синезубый") ||
           name.Equals("Выползень");
}
```

---

## 8. Интеграция с Android

### Необходимые изменения

1. **Создание окна настроек автобоя** (Activity/Dialog)
   - Показать все настройки из FormSettingsAb
   - Сохранять в Profile

2. **Интеграция с LezFight в Android**
   - LezFight.java уже портирован
   - Нужно добавить вызов из PostFilter

3. **Обработка состояний автобоя**
   - Интегрировать с AutoFunctionsManager
   - Обрабатывать состояния: AutoboiOff, AutoboiOn, Timeout, Restoring

4. **Отслеживание информации о бое**
   - Парсить HTML верхнего фрейма
   - Определять тип противника
   - Показывать уведомления

---

## 9. План реализации

### Этап 1: Форма настроек автобоя

- [ ] Создать `AutoboiSettingsActivity.java` или `AutoboiSettingsFragment.java`
- [ ] Реализовать все вкладки из FormSettingsAb
- [ ] Интегрировать с Profile (сохранение/загрузка)
- [ ] Добавить UI для управления LezGroups

### Этап 2: Интеграция LezFight

- [ ] Добавить вызов LezFight из PostFilter
- [ ] Обработать Result и Frame
- [ ] Интегрировать с AutoFunctionsManager

### Этап 3: Обработка состояний

- [ ] Реализовать логику Timeout
- [ ] Реализовать логику Restoring
- [ ] Добавить уведомления в чат

### Этап 4: Отслеживание противника

- [ ] Парсить информацию о противнике из HTML
- [ ] Определять тип (бот/человек/босс/невидимка)
- [ ] Показывать Tray уведомления

---

## 10. Файлы для изучения

### Уже портированы (проверить полноту)

1. `app/.../lez/LezFight.java` - основной класс
2. `app/.../lez/LezBotsGroup.java` - группа ботов
3. `app/.../lez/LezBotsClass.java` - класс бота
4. `app/.../lez/LezBotsClassCollection.java` - коллекция классов
5. `app/.../lez/LezSpell.java` - заклинание
6. `app/.../lez/LezSpellCollection.java` - коллекция заклинаний
7. `app/.../lez/LezNode.java` - узел комбинации
8. `app/.../model/AutoboiState.java` - состояния автобоя

### Требуют портирования

1. `FormSettingsAb.cs` → `AutoboiSettingsActivity.java`
2. Интеграция с PostFilter
3. Profile переменные (LezDoAutoboi и др.)

---

## 11. Ключевые методы для реализации

### LezFight.Parse()

Основной метод парсинга HTML боя:
1. Проверка валидности HTML
2. Извлечение параметров игрока (HP, MA)
3. Извлечение параметров противника
4. Определение группы противника
5. Генерация доступных комбинаций
6. Выбор лучшей комбинации
7. Формирование Result и Frame

### Интеграция с MainPhpFight

```csharp
// Из MainPhpFight.cs
if (AppVars.Profile.LezDoAutoboi) {
    if (fight.IsBoi) {
        // Мы в бою
        if (!fight.DoStop && !fight.IsLowHp && !fight.IsLowMa && !fight.DoExit) {
            // Продолжаем бой
            return fight.Frame;
        }
        // Обработка остановки
    } else {
        // Бой завершился
        // Обработка завершения боя
    }
}
```

---

## 12. Зависимости

### Внутри Lez

- LezFight → LezBotsGroup, LezSpellCollection, LezNode, LezBotsClassCollection
- LezBotsGroup → LezBotsClass, LezSpellCollection
- LezNode → LezSpell

### Внешние зависимости

- LezFight → AppVars.Profile (настройки)
- LezFight → AppVars.ContentMainPhp (HTML)
- MainPhpFight → LezFight, AppVars, AutoboiState

---

## 13. Тестирование

### Юнит-тесты

1. Тестирование парсинга HTML боя
2. Тестирование генерации комбинаций
3. Тестирование выбора лучшей комбинации
4. Тестирование сохранения/загрузки профиля

### Интеграционные тесты

1. Тестирование полного цикла автобоя
2. Тестирование переключения состояний
3. Тестирование UI настроек
