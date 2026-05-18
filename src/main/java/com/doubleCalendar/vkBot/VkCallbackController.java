package com.doubleCalendar.vkBot;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/vk")
@RequiredArgsConstructor
public class VkCallbackController {

    private final VkBot vkBot;
    private final VkConfig vkConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @PostMapping("/callback")
    public String handleCallback(@RequestBody Map<String, Object> body) {
        log.info("📨 Получен callback: type={}", body.get("type"));

        String type = (String) body.get("type");

        // Подтверждение адреса сервера
        if ("confirmation".equals(type)) {
            log.info("✅ Возвращаем confirmation code: {}", vkConfig.getConfirmationCode());
            return vkConfig.getConfirmationCode();
        }

        // Проверка секретного ключа
        if (vkConfig.getSecretKey() != null && !vkConfig.getSecretKey().isEmpty()) {
            String receivedSecret = (String) body.get("secret");
            if (receivedSecret == null || !vkConfig.getSecretKey().equals(receivedSecret)) {
                log.warn("❌ Invalid secret key: expected={}, got={}", vkConfig.getSecretKey(), receivedSecret);
                return "ok";
            }
        }

        // Обработка нового сообщения
        if ("message_new".equals(type)) {
            try {
                Map<String, Object> object = (Map<String, Object>) body.get("object");
                Map<String, Object> message = (Map<String, Object>) object.get("message");
                Integer peerId = (Integer) message.get("peer_id");
                String text = (String) message.get("text");
                Integer messageId = (Integer) message.get("id");

                // 🔘 Обработка payload от кнопок клавиатуры
                String payload = (String) message.get("payload");
                if (payload != null && !payload.isEmpty()) {
                    try {
                        ObjectNode payloadNode = objectMapper.readValue(payload, ObjectNode.class);
                        String cmd = payloadNode.get("cmd").asText();
                        text = "/" + cmd;
                        log.info("🔘 Кнопка нажата: label='{}' → команда='{}'", message.get("text"), text);
                    } catch (Exception e) {
                        log.warn("❌ Невалидный payload: {}", payload);
                    }
                }

                log.info("📝 Новое сообщение от peerId={}: {}", peerId, text);
                vkBot.handleMessage(peerId, text, messageId);

            } catch (Exception e) {
                log.error("❌ Ошибка обработки сообщения: {}", e.getMessage(), e);
            }
        }

        return "ok";
    }
}