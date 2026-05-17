// CalendarSyncScheduler.java
package com.doubleCalendar.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sync.enabled", havingValue = "true", matchIfMissing = true)
public class CalendarSyncScheduler {

    private final YandexCalendarService calendarService;

    @Scheduled(fixedDelayString = "${sync.fixed-delay:300000}",
            initialDelayString = "${sync.initial-delay:60000}")
    public void syncCalendars() {
        log.info("Запуск плановой синхронизации календарей");
        calendarService.syncCalendars();
    }
}