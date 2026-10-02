package com.doubleCalendar.memberBot;

import jakarta.annotation.PostConstruct;
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

    /** Файл с peerId получателей уведомлений. */
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

    /**
     * Выводит в лог наглядный итог конфигурации, чтобы сразу было видно, настроен ли бот.
     */
    @PostConstruct
    public void logConfig() {
        log.info("⚙️ Member Bot: enabled={}, accessToken={}, groupId={}, confirmationCode={}, secretKey={}, subscribersFile={}",
                enabled,
                isBlank(accessToken) ? "ОТСУТСТВУЕТ" : "задан (" + mask(accessToken) + ")",
                groupId,
                isBlank(confirmationCode) ? "ОТСУТСТВУЕТ" : "задан",
                isBlank(secretKey) ? "ОТСУТСТВУЕТ (secret не проверяется)" : "задан",
                subscribersFile);

        if (!isValid()) {
            log.warn("⚠️ Member Bot НЕ будет отвечать: задайте в .env переменные VK_MEMBER_TOKEN и VK_MEMBER_GROUP_ID (и VK_MEMBER_CONFIRMATION_CODE)");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String mask(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }
}
