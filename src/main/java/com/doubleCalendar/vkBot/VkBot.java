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

    /**
     * Отправка простого сообщения
     */
    public void sendMessage(Integer peerId, String text) {
        messageSender.sendMessage(peerId, text);
    }

    /**
     * Отправка сообщения с клавиатурой
     */
    public void sendMessageWithKeyboard(Integer peerId, String text, String keyboardJson) {
        messageSender.sendMessageWithKeyboard(peerId, text, keyboardJson);
    }

    /**
     * Проверка инициализации
     */
    public boolean isInitialized() {
        return messageSender.isInitialized();
    }


    /**
     * Отправка уведомления о статусе синхронизации
     */
    public void notifySyncStatus(String status) {
        if (vkConfig.getAdminId() != null) {
            messageSender.sendMessage(vkConfig.getAdminId(), "📊 " + status);
        }
    }

    /**
     * Отправка уведомления об ошибке синхронизации
     */
    public void notifySyncError(String error) {
        if (vkConfig.getAdminId() != null) {
            messageSender.sendMessage(vkConfig.getAdminId(), "❌ Ошибка синхронизации: " + error);
        }
    }

    /**
     * Отправка уведомления о созданной копии события
     */
    public void notifyEventCopied(String summary, String date) {
        if (vkConfig.getAdminId() != null) {
            String message = String.format("📅 Создана копия события:\n%s\nДата: %s", summary, date);
            messageSender.sendMessage(vkConfig.getAdminId(), message);
        }
    }

    /**
     * Отправка уведомления об удаленной копии события
     */
    public void notifyEventCopyDeleted(String summary) {
        if (vkConfig.getAdminId() != null) {
            messageSender.sendMessage(vkConfig.getAdminId(), "🗑️ Удалена копия события: " + summary);
        }
    }
}