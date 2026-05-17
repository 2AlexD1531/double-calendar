// Calendar2Config.java
package com.doubleCalendar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "calendar2")
public class Calendar2Config {
    private String url;
    private String username;
    private String password;
    private String calendarName;
}