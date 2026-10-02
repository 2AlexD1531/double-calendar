package com.doubleCalendar.memberBot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberBotKeyboardFactory {

    private final ObjectMapper objectMapper;

    /**
     * Главное меню бота участников
     */
    public String createMainKeyboard() {
        ObjectNode keyboard = newKeyboard();
        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "📅 Посмотреть ближайшие даты", "upcoming", "primary");

        ArrayNode row2 = buttons.addArray();
        addButton(row2, "🔔 Подписаться", "subscribe", "positive");
        addButton(row2, "🔕 Отписаться", "unsubscribe", "secondary");

        return toJson(keyboard);
    }

    /**
     * Клавиатура под уведомлением о брони
     */
    public String createNotificationKeyboard() {
        ObjectNode keyboard = newKeyboard();
        ArrayNode buttons = keyboard.putArray("buttons");

        ArrayNode row1 = buttons.addArray();
        addButton(row1, "📅 Посмотреть ближайшие даты", "upcoming", "primary");

        ArrayNode row2 = buttons.addArray();
        addButton(row2, "🔕 Отписаться", "unsubscribe", "secondary");

        return toJson(keyboard);
    }

    private ObjectNode newKeyboard() {
        ObjectNode keyboard = objectMapper.createObjectNode();
        keyboard.put("one_time", false);
        keyboard.put("inline", false);
        return keyboard;
    }

    private void addButton(ArrayNode row, String label, String command, String color) {
        ObjectNode button = objectMapper.createObjectNode();
        ObjectNode action = button.putObject("action");
        action.put("type", "text");
        action.put("label", label);
        action.put("payload", "{\"cmd\":\"" + command + "\"}");
        button.put("color", color);
        row.add(button);
    }

    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.error("Ошибка конвертации в JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
