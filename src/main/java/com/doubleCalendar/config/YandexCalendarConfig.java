package com.doubleCalendar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "yandex.calendar")
public class YandexCalendarConfig {
    private String url;
    private String username;
    private String password;
    private String calendarName;



    // Настройки синхронизации
    private int syncLookbackMonths = 3;
    private int syncLookaheadWeeks = 4;

    // Настройки подключения
    private int connectTimeoutSeconds = 15;
    private int readTimeoutSeconds = 30;
}