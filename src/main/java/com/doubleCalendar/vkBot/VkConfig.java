package com.doubleCalendar.vkBot;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "vk.bot")
public class VkConfig {

    private boolean enabled = false;
    private String accessToken = "";
    private Integer groupId = 0;
    private Integer adminId = 0;
    private String confirmationCode = "";
    private String secretKey = "";
    private String apiVersion = "5.199";
    private int connectionTimeout = 30;
    private int readTimeout = 30;
    private int maxButtonsPerRow = 4;
    private int maxRows = 10;
    private boolean logMessages = false;
    private boolean autoSyncOnStartup = true;


    @PostConstruct

    public boolean isValid() {
        if (!enabled) {
            return false;
        }

        boolean valid = accessToken != null && !accessToken.isEmpty() &&
                groupId != null && groupId > 0 &&
                adminId != null && adminId > 0;

        if (!valid) {
            log.warn("VK Bot конфигурация невалидна. Проверьте accessToken, groupId, adminId");
        }

        return valid;
    }

}