package com.doubleCalendar.calendar;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * Изменение брони в календаре 1: новое событие или отмена события.
 * Публикуется из {@link YandexCalendarService} при синхронизации.
 */
@Getter
@RequiredArgsConstructor
public class CalendarChangeEvent {

    public enum Type {
        CREATED,
        CANCELLED
    }

    private final Type type;
    private final CalendarEventData event;
    private final LocalDateTime occurredAt;

    public static CalendarChangeEvent created(CalendarEventData event) {
        return new CalendarChangeEvent(Type.CREATED, event, LocalDateTime.now());
    }

    public static CalendarChangeEvent cancelled(CalendarEventData event) {
        return new CalendarChangeEvent(Type.CANCELLED, event, LocalDateTime.now());
    }

    public boolean isCreated() {
        return type == Type.CREATED;
    }

    public String getSummary() {
        return event.getSummary();
    }

    public String getDescription() {
        return event.getDescription();
    }

    public LocalDateTime getStart() {
        return event.getStart();
    }

    public LocalDateTime getEnd() {
        return event.getEnd();
    }

    public boolean isAllDay() {
        return event.isAllDay();
    }
}
