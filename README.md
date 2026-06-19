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



## Лицензия

MIT License — можно использовать, менять и распространять свободно.

---

Подробнее в [LICENSE](LICENSE)