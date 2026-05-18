package com.doubleCalendar.vkBot;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VkBot {

    private final VkConfig vkConfig;
    private final VkCommandHandler commandHandler;
    private final VkMessageSender messageSender;


    @PostConstruct
    public void init() {
        if (!vkConfig.isValid()) {
            log.warn("VK Bot не инициализирован: невалидная конфигурация");
            return;
        }

        try {
            VkApiClient vk = new VkApiClient(new HttpTransportClient());
            GroupActor actor = new GroupActor(vkConfig.getGroupId(), vkConfig.getAccessToken());

            // Инициализируем VK API в обработчиках
            commandHandler.initVkApi(vk, actor);
            messageSender.initVkApi(vk, actor);

            log.info("VK Bot инициализирован. Admin ID: {}", vkConfig.getAdminId());
        } catch (Exception e) {
            log.error("Ошибка инициализации VK: {}", e.getMessage());
        }
    }

    /**
     * Обработка входящего сообщения
     */
    public void handleMessage(Integer peerId, String text, Integer messageId) {
        if (!vkConfig.isValid()) {
            log.warn("VK Bot не инициализирован");
            return;
        }

        // Проверка прав администратора
        if (vkConfig.getAdminId() != null && !peerId.equals(vkConfig.getAdminId())) {
            log.warn("Сообщение от неадминистратора: peerId={}, adminId={}",
                    peerId, vkConfig.getAdminId());
            messageSender.sendMessage(peerId, "⛔ У вас нет доступа к админ-панели.");
            return;
        }

        log.info("✅ Команда администратора: peerId={}, text={}", peerId, text);
        commandHandler.handleCommand(peerId, text, messageId);
    }


}