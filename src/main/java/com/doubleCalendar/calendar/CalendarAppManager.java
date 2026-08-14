package com.doubleCalendar.calendar;

import com.doubleCalendar.config.Calendar2Config;
import com.doubleCalendar.config.YandexCalendarConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarAppManager {

    private final YandexCalendarConfig calendar1Config;
    private final Calendar2Config calendar2Config;
    private final YandexCalendarService calendarService;

    private volatile boolean initialized = false;
    private volatile boolean syncEnabled = false;

    /**
     * Инициализация календарей с переданными параметрами
     */
    public String initialize(Map<String, String> params) {
        log.info("Инициализация календарей с параметрами");

        try {
            // Валидация параметров
            String validationError = validateParams(params);
            if (validationError != null) {
                return "❌ " + validationError;
            }

            // Установка параметров для календаря 1
            calendar1Config.setUrl(params.get("calendar1_url"));
            calendar1Config.setUsername(params.get("calendar1_username"));
            calendar1Config.setPassword(params.get("calendar1_password"));
            calendar1Config.setCalendarName(params.get("calendar1_name"));

            // Установка параметров для календаря 2
            calendar2Config.setUrl(params.get("calendar2_url"));
            calendar2Config.setUsername(params.get("calendar2_username"));
            calendar2Config.setPassword(params.get("calendar2_password"));
            calendar2Config.setCalendarName(params.get("calendar2_name"));

            // Пробуем инициализировать сервис
            boolean initResult = calendarService.initialize();

            if (initResult) {
                initialized = true;
                syncEnabled = true;


                CalendarStats stats = calendarService.getCalendarStats();

                String result = "✅ Календари успешно инициализированы!\n\n" +
                        "📋 Конфигурация:\n" +
                        "Календарь 1: " + calendar1Config.getCalendarName() + "\n" +
                        "Пользователь: " + calendar1Config.getUsername() + "\n" +
                        "Календарь 2: " + calendar2Config.getCalendarName() + "\n" +
                        "Пользователь: " + calendar2Config.getUsername() + "\n\n" +
                        "📊 Статистика календаря 1:\n" +
                        "Всего событий: " + stats.getTotalEvents() + "\n" +
                        "Прошедших: " + stats.getPastEvents() + "\n" +
                        "Будущих: " + stats.getFutureEvents() + "\n" +
                        "Сегодня: " + stats.getTodayEvents() + "\n\n" +
                        "Синхронизация запущена.";

                log.info("✅ Календари успешно инициализированы");
                return result;
            } else {
                return "❌ Не удалось подключиться к календарям. Проверьте данные.";
            }

        } catch (Exception e) {
            log.error("Ошибка инициализации: {}", e.getMessage(), e);
            return "❌ Ошибка инициализации: " + e.getMessage();
        }
    }

    public String getStatus() {
        if (!initialized) {
            return "❌ Календари не инициализированы\n" +
                    "Используйте команду:\n" +
                    "/init [параметры]";
        }

        CalendarStats stats = calendarService.getCalendarStats();
        String syncInfo = calendarService.getLastSyncInfo();

        return "📊 Статус синхронизации:\n" +
                "━━━━━━━━━━━━━━━━━━\n" +
                "Статус: " + (syncEnabled ? "✅ Активна" : "⏸️ Остановлена") + "\n\n" +
                "📋 Календарь 1:\n" +
                "Имя: " + calendar1Config.getCalendarName() + "\n" +
                "Пользователь: " + calendar1Config.getUsername() + "\n" +
                "Всего событий: " + stats.getTotalEvents() + "\n" +
                "Прошедших: " + stats.getPastEvents() + "\n" +
                "Будущих: " + stats.getFutureEvents() + "\n" +
                "Сегодня: " + stats.getTodayEvents() + "\n\n" +
                "📋 Календарь 2:\n" +
                "Имя: " + calendar2Config.getCalendarName() + "\n" +
                "Пользователь: " + calendar2Config.getUsername() + "\n" +
                "Событий-копий: " + stats.getTotalEvents() + "\n\n" +
                (syncInfo != null ? "🔄 " + syncInfo + "\n" : "");
    }

    /**
     * Валидация входящих параметров
     */
    private String validateParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "Не переданы параметры";
        }

        String[] required = {
                "calendar1_url", "calendar1_username", "calendar1_password", "calendar1_name",
                "calendar2_url", "calendar2_username", "calendar2_password", "calendar2_name"
        };

        for (String field : required) {
            if (!params.containsKey(field) || params.get(field) == null || params.get(field).trim().isEmpty()) {
                return "Отсутствует обязательный параметр: " + field;
            }
        }

        // Валидация URL
        String url1 = params.get("calendar1_url");
        String url2 = params.get("calendar2_url");

        if (!url1.startsWith("https://")) {
            return "❌ Календарь 1 должен использовать HTTPS (защищенное соединение)";
        }
        if (!url2.startsWith("https://")) {
            return "❌ Календарь 2 должен использовать HTTPS (защищенное соединение)";
        }

        return null; // Всё OK
    }

    /**
     * Запуск синхронизации
     */
    public String startSync() {
        if (!initialized) {
            return "❌ Календари не инициализированы. Используйте команду /start";
        }

        if (syncEnabled) {
            return "⚠️ Синхронизация уже запущена";
        }

        try {
            calendarService.syncCalendars();
            syncEnabled = true;
            log.info("✅ Синхронизация запущена");
            return "✅ Синхронизация запущена";
        } catch (Exception e) {
            log.error("Ошибка запуска синхронизации: {}", e.getMessage(), e);
            return "❌ Ошибка запуска синхронизации: " + e.getMessage();
        }
    }

    /**
     * Остановка синхронизации
     */
    public String stopSync() {
        if (!initialized) {
            return "❌ Календари не инициализированы";
        }

        if (!syncEnabled) {
            return "⚠️ Синхронизация уже остановлена";
        }

        syncEnabled = false;
        log.info("✅ Синхронизация остановлена");
        return "✅ Синхронизация остановлена";
    }

    /**
     * Ручная синхронизация
     */
    public String manualSync() {
        if (!initialized) {
            return "❌ Календари не инициализированы. Используйте команду /start";
        }

        try {
            calendarService.syncCalendars();
            log.info("✅ Ручная синхронизация выполнена");
            return "✅ Синхронизация выполнена";
        } catch (Exception e) {
            log.error("Ошибка синхронизации: {}", e.getMessage(), e);
            return "❌ Ошибка синхронизации: " + e.getMessage();
        }
    }



    /**
     * Сброс конфигурации
     */
    public String reset() {
        initialized = false;
        syncEnabled = false;

        calendar1Config.setUrl(null);
        calendar1Config.setUsername(null);
        calendar1Config.setPassword(null);
        calendar1Config.setCalendarName(null);

        calendar2Config.setUrl(null);
        calendar2Config.setUsername(null);
        calendar2Config.setPassword(null);
        calendar2Config.setCalendarName(null);

        log.info("✅ Конфигурация сброшена");
        return "✅ Конфигурация сброшена";
    }
}