package com.doubleCalendar.vkBot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VkKeyboardFactory {

    private final ObjectMapper objectMapper;

    /**
     * Главное меню
     */
    public String createMainKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");

        // Ряд 1: Основные действия
        ArrayNode row1 = buttons.addArray();
        addButton(row1, "📋 Инициализация", "init", ButtonColor.PRIMARY);
        addButton(row1, "📊 Статус", "status", ButtonColor.PRIMARY);

        // Ряд 2: Управление синхронизацией
        ArrayNode row2 = buttons.addArray();
        addButton(row2, "▶️ Запустить", "start_sync", ButtonColor.POSITIVE);
        addButton(row2, "⏸️ Остановить", "stop_sync", ButtonColor.NEGATIVE);

        // Ряд 3: Дополнительно
        ArrayNode row3 = buttons.addArray();
        addButton(row3, "🔄 Синхронизировать", "sync", ButtonColor.PRIMARY);
        addButton(row3, "🔄 Сбросить", "reset", ButtonColor.NEGATIVE);

        // Ряд 4: Помощь
        ArrayNode row4 = buttons.addArray();
        addButton(row4, "❓ Помощь", "help", ButtonColor.SECONDARY);

        return toJson(keyboard);
    }

    /**
     * Клавиатура для отмены
     */
    public String createCancelKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");
        ArrayNode row1 = buttons.addArray();
        addButton(row1, "❌ Отмена", "cancel", ButtonColor.NEGATIVE);

        return toJson(keyboard);
    }

    /**
     * Клавиатура подтверждения инициализации
     */
    public String createConfirmKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "✅ Подтвердить", "confirm_init", ButtonColor.POSITIVE);
        addButton(row1, "❌ Отмена", "cancel", ButtonColor.NEGATIVE);

        return toJson(keyboard);
    }

    /**
     * Клавиатура управления синхронизацией
     */
    public String createSyncControlKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "▶️ Запустить", "start_sync", ButtonColor.POSITIVE);
        addButton(row1, "⏸️ Остановить", "stop_sync", ButtonColor.NEGATIVE);

        ArrayNode row2 = buttons.addArray();
        addButton(row2, "🔄 Синхронизировать", "sync", ButtonColor.PRIMARY);
        addButton(row2, "📊 Статус", "status", ButtonColor.PRIMARY);

        ArrayNode row3 = buttons.addArray();
        addButton(row3, "◀ Назад", "menu", ButtonColor.SECONDARY);

        return toJson(keyboard);
    }

    /**
     * Клавиатура для статуса
     */
    public String createStatusKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "🔄 Обновить статус", "status", ButtonColor.PRIMARY);
        addButton(row1, "📋 Инициализация", "init", ButtonColor.PRIMARY);

        ArrayNode row2 = buttons.addArray();
        addButton(row2, "◀ Назад", "menu", ButtonColor.SECONDARY);

        return toJson(keyboard);
    }

    /**
     * Клавиатура после успешной инициализации
     */
    public String createPostInitKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "▶️ Запустить синхронизацию", "start_sync", ButtonColor.POSITIVE);
        addButton(row1, "📊 Статус", "status", ButtonColor.PRIMARY);

        ArrayNode row2 = buttons.addArray();
        addButton(row2, "🔄 Синхронизировать", "sync", ButtonColor.PRIMARY);
        addButton(row2, "◀ В меню", "menu", ButtonColor.SECONDARY);

        return toJson(keyboard);
    }

    /**
     * Клавиатура помощи
     */
    public String createHelpKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "📋 Инициализация", "init", ButtonColor.PRIMARY);
        addButton(row1, "📊 Статус", "status", ButtonColor.PRIMARY);

        ArrayNode row2 = buttons.addArray();
        addButton(row2, "◀ В меню", "menu", ButtonColor.SECONDARY);

        return toJson(keyboard);
    }

    /**
     * Клавиатура для ошибки
     */
    public String createErrorKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);

        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "🔄 Повторить", "init", ButtonColor.PRIMARY);
        addButton(row1, "◀ В меню", "menu", ButtonColor.SECONDARY);

        return toJson(keyboard);
    }

    /**
     * Добавление кнопки с цветом
     */
    private void addButton(ArrayNode row, String label, String command, ButtonColor color) {
        ObjectNode button = objectMapper.createObjectNode();
        ObjectNode action = button.putObject("action");
        action.put("type", "text");
        action.put("label", label);
        action.put("payload", "{\"cmd\":\"" + command + "\"}");

        if (color != null) {
            button.put("color", color.getValue());
        }

        row.add(button);
    }

    /**
     * Конвертация в JSON строку
     */
    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.error("Ошибка конвертации в JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Цвета кнопок
     */
    public enum ButtonColor {
        PRIMARY("primary"),
        SECONDARY("secondary"),
        POSITIVE("positive"),
        NEGATIVE("negative");

        private final String value;

        ButtonColor(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}