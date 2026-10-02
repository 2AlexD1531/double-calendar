package com.doubleCalendar.memberBot;

import com.doubleCalendar.calendar.CalendarChangeEvent;
import com.doubleCalendar.calendar.CalendarEventData;
import com.doubleCalendar.calendar.YandexCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Уведомления участников календаря в ВК:
 * новая бронь, отмена брони и список ближайших дат.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberNotificationService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final MemberBotConfig memberConfig;
    private final MemberBotMessageSender messageSender;
    private final MemberSubscriberStore subscriberStore;
    private final MemberBotKeyboardFactory keyboardFactory;
    private final RecentEventsFormatter recentEventsFormatter;
    private final YandexCalendarService calendarService;

    /**
     * Рассылка уведомления об изменении брони всем подписчикам.
     */
    @EventListener
    public void onCalendarChanged(CalendarChangeEvent event) {
        if (!memberConfig.isValid()) {
            log.warn("Уведомление не отправлено: Member Bot выключен или не настроен");
            return;
        }

        List<Integer> recipients = subscriberStore.getRecipients();
        if (recipients.isEmpty()) {
            log.info("Уведомление «{}» не отправлено: нет подписчиков", describe(event));
            return;
        }

        List<CalendarEventData> upcoming = loadUpcomingEvents();
        String compactList = recentEventsFormatter.formatCompactList(
                upcoming, RecentEventsFormatter.NOTIFICATION_EVENTS_LIMIT);
        String message = buildNotificationMessage(event, compactList);
        String keyboard = keyboardFactory.createNotificationKeyboard();

        for (Integer peerId : recipients) {
            messageSender.sendMessageWithKeyboard(peerId, message, keyboard);
        }

        log.info("✅ Уведомление «{}» отправлено {} подписчикам", describe(event), recipients.size());
    }

    /**
     * Текст уведомления: событие + 10 ближайших дат.
     */
    private String buildNotificationMessage(CalendarChangeEvent event, String compactList) {
        StringBuilder sb = new StringBuilder();

        if (event.isCreated()) {
            sb.append("🆕 Новая бронь!\n");
        } else {
            sb.append("❌ Событие отменено!\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━\n");
        sb.append("📌 ").append(safeTitle(event.getSummary())).append("\n");
        sb.append("🗓 ")
                .append(recentEventsFormatter.formatWhenWithWeekday(
                        event.getStart(), event.getEnd(), event.isAllDay()))
                .append("\n");

        String description = event.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            sb.append("📝 ").append(description.replaceAll("\\s+", " ").trim()).append("\n");
        }

        sb.append("🕐 ")
                .append(event.getOccurredAt().format(TIME_FORMAT))
                .append("\n\n");

        sb.append("📅 10 ближайших дат:\n");
        sb.append(compactList).append("\n\n");
        sb.append("Нажмите кнопку, чтобы посмотреть 5 ближайших дат с описанием.");

        return sb.toString();
    }

    /**
     * 5 ближайших дат с описанием — ответ на кнопку «Посмотреть ближайшие даты».
     */
    public String buildUpcomingEventsMessage() {
        List<CalendarEventData> upcoming = loadUpcomingEvents();
        String detailedList = recentEventsFormatter.formatDetailedList(
                upcoming, RecentEventsFormatter.UPCOMING_EVENTS_LIMIT);

        return "📅 Ближайшие даты (5):\n" +
                "━━━━━━━━━━━━━━━━━━\n" +
                detailedList;
    }

    private List<CalendarEventData> loadUpcomingEvents() {
        try {
            return calendarService.getUpcomingEventsWithDescription();
        } catch (Exception e) {
            log.error("Ошибка получения ближайших событий: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private String describe(CalendarChangeEvent event) {
        String type = event.isCreated() ? "новая бронь" : "отмена";
        return type + ": " + safeTitle(event.getSummary()) + " (" +
                recentEventsFormatter.formatWhen(event.getStart(), event.isAllDay()) + ")";
    }

    private String safeTitle(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return "Без названия";
        }
        return summary.trim();
    }
}
