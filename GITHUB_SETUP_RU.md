# Как создать репозиторий Grand Builder на GitHub

Ниже инструкция сделана так, чтобы ты мог просто открыть страницу GitHub и буквально копировать готовые значения без угадывания.

## Что вписать на странице Create a new repository

Открой страницу создания нового репозитория и заполни поля так:

### Блок General

`Owner`
- выбери: `login34245`

`Repository name`
- впиши: `Grand-Builder`

`Description`
- вставь этот текст:

```text
Эффектный Fabric-мод для Minecraft 1.21.11: анимированная постройка больших структур, предпросмотр, управление скоростью, откат, свои схематики и поддержка внешних форматов.
```

### Блок Configuration

`Choose visibility`
- выбери: `Public`

`Add README`
- поставь: `Off`

Почему именно `Off`:
- в проекте уже есть свой `README.md`
- если включить этот переключатель на GitHub, GitHub создаст ещё один README на сервере
- потом при первой отправке файлов могут появиться лишние конфликты

`Add .gitignore`
- выбери: `No .gitignore`

Почему:
- в проекте уже есть локальный файл `.gitignore`

`Add license`
- выбери: `No license`

Почему:
- в проекте уже есть локальный файл `LICENSE`

После этого нажми кнопку `Create repository`.

## Что делать сразу после создания репозитория

После нажатия `Create repository` GitHub откроет страницу нового пустого репозитория и покажет блок:

`…or push an existing repository from the command line`

Теперь тебе нужно открыть терминал именно в папке проекта:

```text
C:\Users\Admin\Documents\програмирование\на пайтоне\codex\minecraft shorts mod\fabric-example-mod-1.21
```

## Команды, которые можно копировать по порядку

## Если терминал пишет `git is not recognized`

Это значит, что Git ещё не установлен в Windows или не добавлен в `PATH`.

Сделай так:
1. Скачай Git for Windows с официального сайта `https://git-scm.com/download/win`
2. Установи Git с обычными настройками.
3. Во время установки оставь вариант, где Git доступен из командной строки.
4. Полностью закрой терминал.
5. Открой терминал заново.
6. Проверь командой:

```powershell
git --version
```

Если появилась версия Git, можно переходить к следующим командам.

Если Git ещё ни разу не настраивался на этом компьютере, сначала один раз выполни:

```powershell
git config --global user.name "Login34245"
git config --global user.email "ТВОЯ_ПОЧТА_ОТ_GITHUB"
```

Потом выполни основные команды:

```powershell
cd "C:\Users\Admin\Documents\програмирование\на пайтоне\codex\minecraft shorts mod\fabric-example-mod-1.21"
git init
git branch -M main
git add .
git commit -m "Initial release: Grand Builder 1.1.0"
git remote add origin https://github.com/Login34245/Grand-Builder.git
git push -u origin main
```

## Что делает каждая команда

`cd "..."`
- переходит в папку мода

`git init`
- создаёт локальный git-репозиторий в этой папке

`git branch -M main`
- задаёт основную ветку с именем `main`

`git add .`
- добавляет все текущие файлы проекта в первый коммит

`git commit -m "Initial release: Grand Builder 1.1.0"`
- создаёт первый снимок проекта с понятным названием

`git remote add origin ...`
- привязывает локальный проект к твоему репозиторию на GitHub

`git push -u origin main`
- отправляет все файлы на GitHub

## Если GitHub попросит вход

Обычно откроется окно авторизации или браузер.

Тебе нужно:
1. Войти в свой аккаунт GitHub.
2. Подтвердить доступ Git для отправки файлов.
3. Дождаться завершения `git push`.

## Если появится ошибка `remote origin already exists`

Значит, в папке уже был привязан другой адрес репозитория.

Тогда вместо команды `git remote add origin ...` выполни:

```powershell
git remote set-url origin https://github.com/Login34245/Grand-Builder.git
git push -u origin main
```

## Если появится ошибка `nothing to commit`

Это значит, что коммит уже был создан ранее.

Тогда просто выполни:

```powershell
git push -u origin main
```

## Если GitHub-страница была создана не пустой

Если ты случайно включил:
- `Add README`
- или `Add license`
- или `Add .gitignore`

то GitHub создаст файлы раньше твоей отправки, и первый push может ругаться.

Самый простой и чистый вариант:
1. Удали этот репозиторий на GitHub.
2. Создай его заново.
3. Обязательно оставь `README`, `.gitignore` и `license` выключенными.

## Что должно быть в итоге на GitHub

После успешной отправки в репозитории должны появиться:
- исходный код мода
- `README.md`
- `LICENSE`
- `CHANGELOG.md`
- `PUBLISHING.md`
- `TESTING_CHECKLIST.md`
- баннер `docs/github-banner.png`

## Как потом загружать обновления

Когда ты что-то поменяешь в моде, используй такой набор команд:

```powershell
cd "C:\Users\Admin\Documents\програмирование\на пайтоне\codex\minecraft shorts mod\fabric-example-mod-1.21"
git add .
git commit -m "Update Grand Builder"
git push
```

## Как сделать страницу Releases

После того как репозиторий уже загружен:
1. Открой свой репозиторий на GitHub.
2. Справа нажми `Releases`.
3. Нажми `Draft a new release`.
4. В поле `Tag` впиши: `v1.1.0`
5. В поле `Release title` впиши: `Grand Builder 1.1.0`
6. В описание релиза можешь вставить краткий список изменений.
7. Прикрепи готовый `.jar`.
8. Нажми `Publish release`.

## Готовый текст для GitHub Release

Если захочешь, можешь вставить такой текст:

```text
Grand Builder 1.1.0

- Анимированная постройка больших структур
- Улучшенный предпросмотр и границы больших схем
- Управление скоростью, пауза и откат
- Сохранение своих структур через Builder Selector
- Поддержка .nbt / .schem / .schematic / .litematic
- Улучшенная работа на dedicated server
```
