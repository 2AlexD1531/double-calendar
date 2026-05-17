package com.doubleCalendar.calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarEventData {
    private LocalDateTime start;
    private LocalDateTime end;
    private String summary;
    private String uid;
    private String description;
    private Long userId;
    private String subscriptionNumber;
    private String subscriptionType;
    private Integer remainingLessons;
    private Integer totalLessons;
    private String validUntil;
    private String previousSubscription;
    private boolean paid;
    private boolean allDay;
    private boolean recurring;
    private String etag; // Добавлено для отслеживания изменений

    public CalendarEventData(LocalDateTime start, LocalDateTime end, String summary,
                             String uid, String description, Long userId) {
        this.start = start;
        this.end = end;
        this.summary = summary;
        this.uid = uid;
        this.description = description;
        this.userId = userId;
    }

    public CalendarEventData(LocalDateTime start, LocalDateTime end, String summary,
                             String uid, String description, Long userId,
                             String subscriptionNumber, String subscriptionType,
                             Integer remainingLessons, Integer totalLessons,
                             String validUntil, String previousSubscription, boolean paid) {
        this.start = start;
        this.end = end;
        this.summary = summary;
        this.uid = uid;
        this.description = description;
        this.userId = userId;
        this.subscriptionNumber = subscriptionNumber;
        this.subscriptionType = subscriptionType;
        this.remainingLessons = remainingLessons;
        this.totalLessons = totalLessons;
        this.validUntil = validUntil;
        this.previousSubscription = previousSubscription;
        this.paid = paid;
    }
}