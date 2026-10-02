package com.doubleCalendar.memberBot;

import com.doubleCalendar.calendar.CalendarEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Формирование списков ближайших дат календаря для сообщений ВК.
 * <p>
 * Для уведомлений — 10 ближайших дат, для кнопки «Посмотреть ближайшие даты» — 5 ближайших дат с описанием.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecentEventsFormatter {

    /** Сколько ближайших дат выводится вместе с уведомлением. */
    public static final int NOTIFICATION_EVENTS_LIMIT = 10;

    /** Сколько ближайших дат выводится по кнопке «Посмотреть ближайшие даты». */
    public static final int UPCOMING_EVENTS_LIMIT = 5;

    private static final int DESCRIPTION_LIMIT = 200;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter WEEKDAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE", new Locale("ru"));

    private static final String[] MONTHS_GENITIVE = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    /**
     * Список ближайших дат (до {@code limit} штук) начиная с сегодняшнего дня.
     */
    public List<CalendarEventData> selectUpcoming(List<CalendarEventData> events, int limit) {
        List<CalendarEventData> upcoming = new ArrayList<>();
        if (events == null || events.isEmpty() || limit <= 0) {
            return upcoming;
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        for (CalendarEventData event : events) {
            if (event != null && event.getStart() != null && !event.getStart().isBefore(todayStart)) {
                upcoming.add(event);
            }
        }

        upcoming.sort(Comparator
                .comparing(CalendarEventData::getStart)
                .thenComparing(e -> e.getSummary() != null ? e.getSummary() : ""));

        if (upcoming.size() > limit) {
            return new ArrayList<>(upcoming.subList(0, limit));
        }
        return upcoming;
    }

    /**
     * Нумерованный список дат для уведомления: «1. 05.10.2026 (пн) 14:00 — Название».
     */
    public String formatCompactList(List<CalendarEventData> events, int limit) {
        List<CalendarEventData> upcoming = selectUpcoming(events, limit);

        if (upcoming.isEmpty()) {
            return "Ближайших дат нет.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upcoming.size(); i++) {
            CalendarEventData event = upcoming.get(i);
            sb.append(i + 1).append(". ")
                    .append(formatWhen(event.getStart(), event.isAllDay()))
                    .append(" — ")
                    .append(safeTitle(event.getSummary()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Подробный список дат с описанием: «1. 05.10.2026 (понедельник) 14:00–15:00\n   Название\n   Описание».
     */
    public String formatDetailedList(List<CalendarEventData> events, int limit) {
        List<CalendarEventData> upcoming = selectUpcoming(events, limit);

        if (upcoming.isEmpty()) {
            return "Ближайших дат нет.";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upcoming.size(); i++) {
            CalendarEventData event = upcoming.get(i);
            sb.append(i + 1).append(". ")
                    .append(formatWhenWithWeekday(event.getStart(), event.getEnd(), event.isAllDay()))
                    .append("\n")
                    .append("   ").append(safeTitle(event.getSummary())).append("\n");

            String description = shorten(event.getDescription());
            if (description != null && !description.isEmpty()) {
                sb.append("   📝 ").append(description).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Дата и время события: «05.10.2026 (пн) 14:00» или «05.10.2026 (пн) — весь день».
     */
    public String formatWhen(LocalDateTime start, boolean allDay) {
        if (start == null) {
            return "дата не указана";
        }
        String date = start.format(DATE_FORMAT) + " (" + weekday(start) + ")";
        return allDay ? date + " — весь день" : date + " " + start.format(TIME_FORMAT);
    }

    /**
     * Дата, день недели и интервал времени: «05.10.2026 (понедельник) 14:00–15:00».
     */
    public String formatWhenWithWeekday(LocalDateTime start, LocalDateTime end, boolean allDay) {
        if (start == null) {
            return "дата не указана";
        }

        String date = start.format(DATE_FORMAT) + " (" + fullWeekday(start) + ")";

        if (allDay) {
            return date + " — весь день";
        }

        String time = start.format(TIME_FORMAT);
        if (end != null) {
            time = time + "–" + end.format(TIME_FORMAT);
        }
        return date + " " + time;
    }

    /**
     * «5 октября» — для текста уведомления.
     */
    public String formatHumanDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "дата не указана";
        }
        return dateTime.getDayOfMonth() + " " + MONTHS_GENITIVE[dateTime.getMonthValue() - 1]
                + " " + dateTime.getYear();
    }

    private String weekday(LocalDateTime dateTime) {
        return dateTime.format(WEEKDAY_FORMAT);
    }

    private String fullWeekday(LocalDateTime dateTime) {
        return dateTime.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("ru"));
    }

    private String safeTitle(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return "Без названия";
        }
        return summary.trim();
    }

    private String shorten(String description) {
        if (description == null) {
            return null;
        }
        String cleaned = description.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > DESCRIPTION_LIMIT) {
            return cleaned.substring(0, DESCRIPTION_LIMIT) + "…";
        }
        return cleaned;
    }
}
