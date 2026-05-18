package com.doubleCalendar.vkBot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.queries.messages.MessagesSendQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
public class VkMessageSender {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private VkApiClient vk;
    private GroupActor actor;

    public VkMessageSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Инициализация VK API
     */
    public void initVkApi(VkApiClient vk, GroupActor actor) {
        this.vk = vk;
        this.actor = actor;
        log.info("VkMessageSender инициализирован с VK API");
    }

    /**
     * Отправка простого сообщения
     */
    public void sendMessage(Integer peerId, String message) {
        if (vk == null || actor == null) {
            log.warn("VK API не инициализирован");
            return;
        }

        try {
            vk.messages().send(actor)
                    .peerId(peerId)
                    .message(message)
                    .randomId(random.nextInt())
                    .execute();
            log.info("✅ Сообщение отправлено в {}", peerId);
        } catch (Exception e) {
            log.error("❌ Ошибка отправки сообщения: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения с клавиатурой
     */
    public void sendMessageWithKeyboard(Integer peerId, String message, String keyboardJson) {
        if (vk == null || actor == null) {
            log.warn("VK API не инициализирован");
            return;
        }

        try {
            log.debug("⌨️ Отправка сообщения с клавиатурой в {}: {}", peerId, keyboardJson);

            MessagesSendQuery query = vk.messages().send(actor)
                    .peerId(peerId)
                    .message(message)
                    .randomId(random.nextInt());

            query.unsafeParam("keyboard", keyboardJson != null ? keyboardJson : "{}");
            query.execute();
            log.info("✅ Сообщение с клавиатурой отправлено в {}", peerId);
        } catch (Exception e) {
            log.error("❌ Ошибка отправки сообщения с клавиатурой: {}", e.getMessage());
        }
    }

}