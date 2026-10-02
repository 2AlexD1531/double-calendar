Вот упрощенная версия README.md:

---

# 📅 Double Calendar Sync

Бот для синхронизации календарей через ВКонтакте.

## Что это?

Приложение автоматически копирует события из одного CalDAV календаря (Яндекс) в другой. 
В одном календаре события с описанием(для всех) в другом просто событие(например для заказчика). Управление через ВК бота.

## Как работает?

1. Вы настраиваете два календаря через бота
2. Бот каждые 5 минут проверяет новые события
3. Новые события автоматически копируются во второй календарь
4. Управление из ВК - запуск, остановка, статус, инициализация, ручная синхронизация.
## Быстрый старт

### 1. Настройка

Создайте `application.yml`:

```yaml
server:
  port: 8080

yandex:
  calendar:
    url: https://caldav.yandex.ru
    username: user1@yandex.ru
    password: your_password
    calendar-name: events-12345

calendar2:
  url: https://caldav.yandex.ru
  username: user2@yandex.ru
  password: your_password
  calendar-name: events-67890

vk:
  bot:
    enabled: true
    access-token: vk1.a.xxxxxx
    group-id: 123456789
    confirmation-code: xxxxxxx

sync:
  enabled: true
  fixed-delay: 300000
```

### 2. Запуск

Настроены Docker и Nginx 

```bash
mvn clean package
java -jar target/double-calendar-sync-1.0.0.jar

или DOCKER 
```



### 3. Настройка бота


```
calendar1_url=https://caldav.yandex.ru
calendar1_username=user1@yandex.ru
calendar1_password=your_password
calendar1_name=events-12345
calendar2_url=https://caldav.yandex.ru
calendar2_username=user2@yandex.ru
calendar2_password=your_password
calendar2_name=events-67890
```



## Бот участников (уведомления о брони)

Отдельный бот в **другом сообществе ВК** (админский бот не затрагивается и остаётся только для администратора).
Участники подписываются на него по приглашению и получают:

- 🆕 **Новая бронь** — при создании нового события в календаре 1;
- ❌ **Событие отменено** — при удалении события;
- вместе с каждым уведомлением — список **10 ближайших дат**;
- кнопка **«📅 Посмотреть ближайшие даты»** — 5 ближайших дат с описанием.

Кнопки: «🔔 Подписаться», «🔕 Отписаться», «📅 Посмотреть ближайшие даты» (команды `/subscribe`, `/unsubscribe`, `/upcoming`).

Уведомления отправляются в момент синхронизации. При первом запуске приложения текущие события запоминаются и
не рассылаются; перезапуск приложения сбрасывает это состояние, поэтому брони/отмены, произошедшие пока приложение
было выключено, не рассылаются.

### Настройка

1. Создайте второе сообщество ВК, включите сообщения и Callback API (события «входящее сообщение»),
   адрес: `https://<домен>/vk/member/callback` (админский бот остаётся на `/vk/callback`).
2. Задайте переменные в `.env`:

```
VK_MEMBER_ENABLED=true
VK_MEMBER_TOKEN=токен_сообщества_участников
VK_MEMBER_GROUP_ID=123456789
VK_MEMBER_CONFIRMATION_CODE=код_подтверждения
VK_MEMBER_SECRET_KEY=секретный_ключ
```

Подписчики хранятся в `data/member-bot-subscribers.txt` (папка `data` смонтирована в docker-compose).

## Лицензия

MIT License — можно использовать, менять и распространять свободно.

---

Подробнее в [LICENSE](LICENSE)