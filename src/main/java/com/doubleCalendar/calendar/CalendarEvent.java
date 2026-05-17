package com.doubleCalendar.calendar;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class CalendarEvent {
    private final String uid;
    private final String title;
    private final LocalDateTime start;
    private final LocalDateTime end;

    public String getId() {
        return uid;
    }
}