package com.doubleCalendar.memberBot;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Настройки бота участников календаря (отдельное сообщество ВК).
 * Админский бот (vk.bot.*) настраивается отдельно и не затрагивается.
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "vk.member-bot")
public class MemberBotConfig {

    private boolean enabled = false;
    private String accessToken = "";
    private Integer groupId = 0;
    private String confirmationCode = "";
    private String secretKey = "";

    /** Файл с peerId подписчиков уведомлений. */
    private String subscribersFile = "data/member-bot-subscribers.txt";

    public boolean isValid() {
        if (!enabled) {
            return false;
        }

        boolean valid = accessToken != null && !accessToken.isEmpty() &&
                groupId != null && groupId > 0;

        if (!valid) {
            log.warn("Member Bot конфигурация невалидна. Проверьте accessToken, groupId");
        }

        return valid;
    }
}
