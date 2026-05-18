package com.doubleCalendar.vkBot;


import com.doubleCalendar.calendar.CalendarAppManager;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VkCommandHandler {

    private final CalendarAppManager calendarAppManager;
    private final VkMessageSender messageSender;
    private final VkKeyboardFactory keyboardFactory;

    // Состояния пользователей для многошаговой инициализации
    private final Map<Integer, String> userState = new HashMap<>();
    private final Map<Integer, Map<String, String>> userInputData = new HashMap<>();




    public void initVkApi(VkApiClient vk, GroupActor actor) {
        messageSender.initVkApi(vk, actor);
        log.info("VkCommandHandler инициализирован с VK API");
    }

    /**
     * Обработка команд
     */
    public void handleCommand(Integer peerId, String text, Integer messageId) {
        if (text == null || text.isEmpty()) {
            sendWithKeyboard(peerId, "❌ Пустая команда", keyboardFactory.createMainKeyboard());
            return;
        }

        text = text.trim();

        // Проверяем состояние пользователя (многошаговая инициализация)
        String state = userState.get(peerId);
        if (state != null && state.startsWith("init_")) {
            handleInitState(peerId, text);
            return;
        }

        // Обработка команд
        switch (text) {
            case "/start":
            case "/menu":
            case "menu":
            case "◀ Назад":
            case "◀ В меню":
                sendMainMenu(peerId);
                break;

            case "/init":
            case "📋 Инициализация":
            case "init":
                startInit(peerId);
                break;

            case "/status":
            case "📊 Статус":
            case "status":
                handleStatus(peerId);
                break;

            case "/sync":
            case "🔄 Синхронизировать":
            case "sync":
                handleSync(peerId);
                break;

            case "/start_sync":
            case "▶️ Запустить":
            case "▶️ Запустить синхронизацию":
            case "start_sync":
                handleStartSync(peerId);
                break;

            case "/stop_sync":
            case "⏸️ Остановить":
            case "⏸️ Остановить синхронизацию":
            case "stop_sync":
                handleStopSync(peerId);
                break;

            case "/reset":
            case "🔄 Сбросить":
            case "reset":
                handleReset(peerId);
                break;

            case "/help":
            case "❓ Помощь":
            case "help":
                handleHelp(peerId);
                break;

            case "/cancel":
            case "cancel":
            case "❌ Отмена":
                cancelOperation(peerId);
                break;

            case "/confirm_init":  // ← ДОБАВИТЬ ЭТУ СТРОКУ
            case "confirm_init":    // ← И ЭТУ
            case "✅ Подтвердить":   // ← И ЭТУ
                confirmInit(peerId);
                break;

            case "🔄 Обновить статус":
                handleStatus(peerId);
                break;

            case "🔄 Повторить":
                startInit(peerId);
                break;

            default:
                sendWithKeyboard(peerId, "❌ Неизвестная команда: " + text + "\nИспользуйте меню:",
                        keyboardFactory.createMainKeyboard());
        }
    }
    /**
     * Главное меню
     */
    private void sendMainMenu(Integer peerId) {
        userState.remove(peerId);
        userInputData.remove(peerId);

        String message = "📋 Главное меню\n" +
                "Управление синхронизацией календарей\n\n" +
                "Выберите действие:";

        sendWithKeyboard(peerId, message, keyboardFactory.createMainKeyboard());
    }

    /**
     * Обработка статуса
     */
    private void handleStatus(Integer peerId) {
        String status = calendarAppManager.getStatus();
        sendWithKeyboard(peerId, status, keyboardFactory.createStatusKeyboard());
    }

    /**
     * Обработка ручной синхронизации
     */
    private void handleSync(Integer peerId) {
        String result = calendarAppManager.manualSync();
        sendWithKeyboard(peerId, result, keyboardFactory.createSyncControlKeyboard());
    }

    /**
     * Запуск автосинхронизации
     */
    private void handleStartSync(Integer peerId) {
        String result = calendarAppManager.startSync();
        sendWithKeyboard(peerId, result, keyboardFactory.createSyncControlKeyboard());
    }

    /**
     * Остановка автосинхронизации
     */
    private void handleStopSync(Integer peerId) {
        String result = calendarAppManager.stopSync();
        sendWithKeyboard(peerId, result, keyboardFactory.createSyncControlKeyboard());
    }

    /**
     * Сброс конфигурации
     */
    private void handleReset(Integer peerId) {
        String result = calendarAppManager.reset();
        sendWithKeyboard(peerId, result, keyboardFactory.createMainKeyboard());
    }

    /**
     * Помощь
     */
    private void handleHelp(Integer peerId) {
        String help =
                "📋 Справка по командам:\n\n" +
                        "📋 Инициализация - Настройка календарей\n" +
                        "📊 Статус - Показать текущий статус\n" +
                        "🔄 Синхронизировать - Ручная синхронизация\n" +
                        "▶️ Запустить - Автосинхронизация\n" +
                        "⏸️ Остановить - Остановить автосинхронизацию\n" +
                        "🔄 Сбросить - Сбросить конфигурацию\n\n" +
                        "При инициализации нужно ввести:\n" +
                        "- URL календарей\n" +
                        "- Username и пароль\n" +
                        "- Имя календаря";

        sendWithKeyboard(peerId, help, keyboardFactory.createHelpKeyboard());
    }


    /**
     * Начало инициализации - ввод всех параметров одним сообщением
     */
    private void startInit(Integer peerId) {
        userState.put(peerId, "init_all_params");

        String message = "🔧 Инициализация календарей\n\n" +
                "Введите все параметры одним сообщением в формате:\n\n" +
                "calendar1_url=https://caldav.yandex.ru\n" +
                "calendar1_username=user1@yandex.ru\n" +
                "calendar1_password=your_password1\n" +
                "calendar1_name=events-12345\n" +
                "calendar2_url=https://caldav.yandex.ru\n" +
                "calendar2_username=user2@yandex.ru\n" +
                "calendar2_password=your_password2\n" +
                "calendar2_name=events-67890\n\n" +
                "Каждый параметр с новой строки.\n" +
                "Для отмены нажмите кнопку ниже.";

        sendWithKeyboard(peerId, message, keyboardFactory.createCancelKeyboard());
    }

    /**
     * Обработка состояний инициализации
     */
    private void handleInitState(Integer peerId, String text) {
        if ("/cancel".equals(text) || "cancel".equals(text) || "❌ Отмена".equals(text)) {
            cancelOperation(peerId);
            return;
        }

        String state = userState.get(peerId);

        if ("init_all_params".equals(state)) {
            handleInitAllParams(peerId, text);
        }
    }

    /**
     * Обработка всех параметров одним сообщением
     */
    private void handleInitAllParams(Integer peerId, String text) {
        Map<String, String> params = parseParams(text);

        // Проверяем, что все обязательные параметры заполнены
        String[] required = {
                "calendar1_url", "calendar1_username", "calendar1_password", "calendar1_name",
                "calendar2_url", "calendar2_username", "calendar2_password", "calendar2_name"
        };

        StringBuilder missingParams = new StringBuilder();
        for (String param : required) {
            if (!params.containsKey(param) || params.get(param).trim().isEmpty()) {
                missingParams.append("❌ ").append(param).append("\n");
            }
        }

        if (missingParams.length() > 0) {
            String message = "⚠️ Не все параметры заполнены:\n\n" +
                    missingParams.toString() + "\n" +
                    "Пример правильного формата:\n\n" +
                    "calendar1_url=https://caldav.yandex.ru\n" +
                    "calendar1_username=user1@yandex.ru\n" +
                    "calendar1_password=pass1\n" +
                    "calendar1_name=events-12345\n" +
                    "calendar2_url=https://caldav.yandex.ru\n" +
                    "calendar2_username=user2@yandex.ru\n" +
                    "calendar2_password=pass2\n" +
                    "calendar2_name=events-67890\n\n" +
                    "Попробуйте еще раз:";

            sendWithKeyboard(peerId, message, keyboardFactory.createCancelKeyboard());
            return;
        }

        // Сохраняем параметры для подтверждения
        userInputData.put(peerId, params);
        userState.remove(peerId);

        // Показываем введенные данные и запрашиваем подтверждение
        String summary = "📋 Проверьте введенные данные:\n\n" +
                "Календарь 1:\n" +
                "URL: " + params.get("calendar1_url") + "\n" +
                "Username: " + params.get("calendar1_username") + "\n" +
                "Password: " + maskPassword(params.get("calendar1_password")) + "\n" +
                "Имя: " + params.get("calendar1_name") + "\n\n" +
                "Календарь 2:\n" +
                "URL: " + params.get("calendar2_url") + "\n" +
                "Username: " + params.get("calendar2_username") + "\n" +
                "Password: " + maskPassword(params.get("calendar2_password")) + "\n" +
                "Имя: " + params.get("calendar2_name");

        sendWithKeyboard(peerId, summary, keyboardFactory.createConfirmKeyboard());
    }

    /**
     * Маскировка пароля для отображения
     */
    private String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "***";
        }
        if (password.length() <= 2) {
            return "**";
        }
        return password.charAt(0) + "***" + password.charAt(password.length() - 1);
    }

    /**
     * Парсинг параметров из текста
     */
    private Map<String, String> parseParams(String text) {
        Map<String, String> params = new HashMap<>();

        // Разбиваем по строкам
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Поддерживаем разные форматы:
            // key=value
            // key: value
            // key = value
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    params.put(parts[0].trim(), parts[1].trim());
                }
            } else if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    params.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        return params;
    }


    /**
     * Подтверждение инициализации
     */
    private void confirmInit(Integer peerId) {
        Map<String, String> data = userInputData.get(peerId);
        if (data == null) {
            sendWithKeyboard(peerId, "❌ Нет данных для инициализации", keyboardFactory.createMainKeyboard());
            return;
        }

        String result = calendarAppManager.initialize(data);
        userInputData.remove(peerId);

        if (result.startsWith("✅")) {
            sendWithKeyboard(peerId, result, keyboardFactory.createPostInitKeyboard());
        } else {
            sendWithKeyboard(peerId, result, keyboardFactory.createErrorKeyboard());
        }
    }

    /**
     * Отмена операции
     */
    private void cancelOperation(Integer peerId) {
        userState.remove(peerId);
        userInputData.remove(peerId);
        sendWithKeyboard(peerId, "❌ Операция отменена", keyboardFactory.createMainKeyboard());
    }

    /**
     * Отправка сообщения с клавиатурой
     */
    private void sendWithKeyboard(Integer peerId, String message, String keyboardJson) {
        messageSender.sendMessageWithKeyboard(peerId, message, keyboardJson);
    }
}