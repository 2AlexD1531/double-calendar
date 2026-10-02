package com.doubleCalendar.memberBot;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.queries.messages.MessagesSendQuery;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Отправка сообщений от имени бота участников (свой токен и своё сообщество).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberBotMessageSender {

    private final MemberBotConfig memberConfig;
    private final Random random = new Random();

    private VkApiClient vk;
    private GroupActor actor;

    @PostConstruct
    public void init() {
        if (!memberConfig.isValid()) {
            log.warn("Member Bot не инициализирован: бот выключен или невалидная конфигурация");
            return;
        }

        try {
            vk = new VkApiClient(new HttpTransportClient());
            actor = new GroupActor(memberConfig.getGroupId(), memberConfig.getAccessToken());
            log.info("Member Bot инициализирован.");
        } catch (Exception e) {
            log.error("Ошибка инициализации Member Bot: {}", e.getMessage());
        }
    }

    public void sendMessageWithKeyboard(Integer peerId, String message, String keyboardJson) {
        if (vk == null || actor == null) {
            log.warn("Member Bot: VK API не инициализирован");
            return;
        }

        try {
            MessagesSendQuery query = vk.messages().send(actor)
                    .peerId(peerId)
                    .message(message)
                    .randomId(random.nextInt());

            query.unsafeParam("keyboard", keyboardJson != null ? keyboardJson : "{}");
            query.execute();
            log.info("✅ Member Bot: сообщение отправлено в {}", peerId);
        } catch (Exception e) {
            log.error("❌ Member Bot: ошибка отправки сообщения в {}: {}", peerId, e.getMessage());
        }
    }
}
