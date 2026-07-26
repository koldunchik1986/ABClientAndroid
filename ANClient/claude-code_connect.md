# Подключение Claude Max к OpenCode на чистой Windows

## 1. Установка Node.js

Если Node.js ещё не установлен:

- Скачай с https://nodejs.org (LTS версию)
- Установи, все галочки по умолчанию
- Проверь в PowerShell:

```powershell
node --version
npm --version
```

## 2. Установка OpenCode

```powershell
npm install --global opencode-ai
```

Проверь:

```powershell
opencode --version
```

Если пишет `opencode не найден`, перезапусти PowerShell.

## 3. Установка плагина CortexKit Anthropic OAuth

Этот плагин добавляет поддержку входа через Claude Pro/Max (OAuth), а не через API-ключ.

```powershell
npm install --global --legacy-peer-deps @cortexkit/opencode-anthropic-auth
```

Флаг `--legacy-peer-deps` нужен, чтобы обойти конфликт зависимостей — это безопасно.

## 4. Настройка конфига OpenCode

Открой файл конфига:

```powershell
notepad "$env:USERPROFILE\.config\opencode\opencode.jsonc"
```

Если файла нет — создай. Приведи к такому виду:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "plugin": ["@cortexkit/opencode-anthropic-auth"]
}
```

Сохрани и закрой.

## 5. Очистка кэша плагинов (на всякий случай)

```powershell
Remove-Item -Path "$env:LOCALAPPDATA\opencode\cache" -Recurse -ErrorAction SilentlyContinue
```

## 6. Вход через Claude Max

В PowerShell выполни:

```powershell
opencode auth login --provider anthropic
```

Появится меню с тремя вариантами:

```
  Login method
> Claude Pro/Max          ← выбери ЭТОТ (стрелка вверх/вниз, Enter)
  Create an API Key       ← НЕ ЭТОТ (создаёт API-ключ без кредитов)
  Manually enter API Key  ← НЕ ЭТОТ
```

**Важно:** выбери именно `Claude Pro/Max`. Если выбрать `Create an API Key` — будет ошибка `Your credit balance is too low`, потому что у Max-подписки нет API-кредитов.

После выбора откроется браузер. Войди в свой Claude Max x20 аккаунт и подтверди. В терминал вставь код авторизации — появится `Login successful`.

## 7. Выбор модели в OpenCode

Запусти OpenCode в папке проекта:

```powershell
cd C:\путь\к\твоему\проекту
opencode
```

В открывшемся TUI введи:

```
/models
```

Выбери любую Claude-модель (например `claude-sonnet-4-6` или `claude-opus-4-6`). Всё будет работать через Max x20 подписку.

## Полезные команды плагина

| Команда | Что делает |
|---------|-----------|
| `/claude-quota` | Показывает квоту Max x20 (5-часовой и 7-дневный лимиты) |
| `/claude-cache` | Управление кэшированием промптов |
| `/claude-routing` | Настройка маршрутизации между аккаунтами |
| `/claude-killswitch` | Авто-отключение при истощении квоты |
| `/claude-fast` | Включение fast mode для Opus моделей |

## Если что-то пошло не так

**Ошибка `credit balance too low`:**
— Ты выбрал `Create an API Key` вместо `Claude Pro/Max`. Сбрось и сделай заново:

```powershell
opencode auth logout anthropic
Remove-Item -Path "$env:USERPROFILE\.config\opencode\anthropic-auth*" -ErrorAction SilentlyContinue
opencode auth login --provider anthropic
```

**Плагин не подхватился:**
— Проверь, что в `opencode.jsonc` есть строка `"plugin": ["@cortexkit/opencode-anthropic-auth"]`.
— Очисти кэш: `Remove-Item -Path "$env:LOCALAPPDATA\opencode\cache" -Recurse`.
— Перезапусти OpenCode.

**Команда opencode не найдена:**
— Закрой и открой PowerShell заново, либо перелогинься.
