package com.doubleCalendar.memberBot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Callback API сообщества бота участников.
 * Адрес в настройках сообщества: https://&lt;домен&gt;/vk/member/callback
 * (админский бот остаётся на /vk/callback).
 */
@Slf4j
@RestController
@RequestMapping("/vk/member")
@RequiredArgsConstructor
public class MemberBotCallbackController {

    private final MemberBotConfig memberConfig;
    private final MemberBotCommandHandler commandHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/callback")
    @SuppressWarnings("unchecked")
    public String handleCallback(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        log.info("📨 Member Bot callback: type={}", type);

        if ("confirmation".equals(type)) {
            return memberConfig.getConfirmationCode();
        }

        if (!memberConfig.isValid()) {
            return "ok";
        }

        if (memberConfig.getSecretKey() != null && !memberConfig.getSecretKey().isEmpty()) {
            String receivedSecret = (String) body.get("secret");
            if (!memberConfig.getSecretKey().equals(receivedSecret)) {
                log.warn("❌ Member Bot: неверный secret key");
                return "ok";
            }
        }

        if ("message_new".equals(type)) {
            try {
                Map<String, Object> object = (Map<String, Object>) body.get("object");
                Map<String, Object> message = (Map<String, Object>) object.get("message");
                Integer peerId = (Integer) message.get("peer_id");
                String text = (String) message.get("text");

                String payload = (String) message.get("payload");
                if (payload != null && !payload.isEmpty()) {
                    try {
                        ObjectNode payloadNode = objectMapper.readValue(payload, ObjectNode.class);
                        text = "/" + payloadNode.get("cmd").asText();
                    } catch (Exception e) {
                        log.warn("❌ Member Bot: невалидный payload: {}", payload);
                    }
                }

                log.info("📝 Member Bot: сообщение от peerId={}: {}", peerId, text);
                commandHandler.handleCommand(peerId, text);
            } catch (Exception e) {
                log.error("❌ Member Bot: ошибка обработки сообщения: {}", e.getMessage(), e);
            }
        }

        return "ok";
    }
}
