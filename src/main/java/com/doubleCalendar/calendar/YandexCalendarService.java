package com.doubleCalendar.calendar;

import com.doubleCalendar.config.Calendar2Config;
import com.doubleCalendar.config.YandexCalendarConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class YandexCalendarService {

    private final RestClient restClient;
    private final YandexCalendarConfig config;
    private final Calendar2Config calendar2Config;

    private String calendar1Url;
    private String calendar2Url;
    private HttpHeaders calendar1Headers;
    private HttpHeaders calendar2Headers;

    private static final DateTimeFormatter ALL_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    // Кэш UID событий для быстрого поиска
    private final Map<String, CalendarEventData> eventCache = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastSyncTime = LocalDateTime.now().minusDays(1);

    @PostConstruct
    public void init() {
        log.info("Инициализация сервиса синхронизации календарей");

        // Настройка первого календаря
        String auth1 = config.getUsername() + ":" + config.getPassword();
        calendar1Headers = new HttpHeaders();
        calendar1Headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth1.getBytes()));
        calendar1Headers.setContentType(MediaType.APPLICATION_XML);
        calendar1Headers.set("Depth", "1");

        calendar1Url = config.getUrl() + "/calendars/" + config.getUsername() + "/" + config.getCalendarName();
        log.info("Calendar 1 URL: {}", calendar1Url);

        // Настройка второго календаря
        String auth2 = calendar2Config.getUsername() + ":" + calendar2Config.getPassword();
        calendar2Headers = new HttpHeaders();
        calendar2Headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth2.getBytes()));
        calendar2Headers.setContentType(MediaType.APPLICATION_XML);

        calendar2Url = calendar2Config.getUrl() + "/calendars/" + calendar2Config.getUsername() + "/" + calendar2Config.getCalendarName();
        log.info("Calendar 2 URL: {}", calendar2Url);
    }

    /**
     * Основной метод синхронизации - вызывается по расписанию
     */
    public void syncCalendars() {
        log.info("=== Начало синхронизации календарей ===");

        try {
            // Получаем все события из первого календаря за последние 3 месяца и следующие 4 недели
            List<CalendarEventData> eventsFromCalendar1 = getAllEvents();
            log.info("Получено {} событий из календаря 1", eventsFromCalendar1.size());

            // Получаем существующие события из второго календаря
            List<CalendarEventData> eventsFromCalendar2 = getAllEventsFromCalendar2();
            Set<String> existingUids = new HashSet<>();
            for (CalendarEventData event : eventsFromCalendar2) {
                existingUids.add(event.getUid());
            }
            log.info("Существующих событий в календаре 2: {}", existingUids.size());

            // Для каждого события из календаря 1 создаем короткую версию в календаре 2
            int createdCount = 0;
            for (CalendarEventData fullEvent : eventsFromCalendar1) {
                if (!existingUids.contains(fullEvent.getUid())) {
                    // Создаем короткую версию (только заголовок)
                    createShortEventInCalendar2(fullEvent);
                    createdCount++;
                    log.info("Создано короткое событие для: {}", fullEvent.getSummary());
                }
            }

            log.info("Синхронизация завершена. Создано новых событий: {}", createdCount);

            // Обновляем кэш
            for (CalendarEventData event : eventsFromCalendar1) {
                eventCache.put(event.getUid(), event);
            }
            lastSyncTime = LocalDateTime.now();

        } catch (Exception e) {
            log.error("Ошибка при синхронизации календарей: {}", e.getMessage(), e);
        }

        log.info("=== Конец синхронизации ===");
    }

    /**
     * Получение всех событий из календаря 1
     */
    public List<CalendarEventData> getAllEvents() {
        try {
            LocalDateTime now = LocalDateTime.now();
            String startStr = now.minusMonths(config.getSyncLookbackMonths())
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            String endStr = now.plusWeeks(config.getSyncLookaheadWeeks())
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

            String reportBody = buildReportXml(startStr, endStr);

            ResponseEntity<String> response = restClient.post()
                    .uri(calendar1Url)
                    .headers(headers -> headers.putAll(calendar1Headers))
                    .body(reportBody)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 207) {
                return parseEvents(response.getBody());
            }
        } catch (Exception e) {
            log.error("Ошибка получения событий из календаря 1: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * Получение всех событий из календаря 2
     */
    private List<CalendarEventData> getAllEventsFromCalendar2() {
        try {
            LocalDateTime now = LocalDateTime.now();
            String startStr = now.minusMonths(config.getSyncLookbackMonths())
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            String endStr = now.plusMonths(3)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

            String reportBody = buildReportXml(startStr, endStr);

            ResponseEntity<String> response = restClient.post()
                    .uri(calendar2Url)
                    .headers(headers -> headers.putAll(calendar2Headers))
                    .body(reportBody)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 207) {
                return parseEvents(response.getBody());
            }
        } catch (Exception e) {
            log.error("Ошибка получения событий из календаря 2: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * Создание короткой версии события в календаре 2 (только заголовок)
     */
    private void createShortEventInCalendar2(CalendarEventData fullEvent) {
        try {
            String uid = fullEvent.getUid();
            String shortSummary = fullEvent.getSummary(); // Только заголовок, без описания

            // Для целодневных событий
            boolean isAllDay = isAllDayEvent(fullEvent);

            String icsContent = buildShortIcsContent(uid, shortSummary, fullEvent.getStart(), fullEvent.getEnd(), isAllDay);

            // Отправляем PUT запрос для создания события
            String eventUrl = calendar2Url + "/" + uid + ".ics";

            HttpHeaders headers = new HttpHeaders();
            headers.putAll(calendar2Headers);
            headers.setContentType(MediaType.TEXT_PLAIN);

            restClient.put()
                    .uri(eventUrl)
                    .headers(h -> h.putAll(headers))
                    .body(icsContent)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Создано короткое событие в календаре 2: {}", shortSummary);

        } catch (Exception e) {
            log.error("Ошибка создания события в календаре 2 для {}: {}", fullEvent.getUid(), e.getMessage());
        }
    }

    /**
     * Проверка, является ли событие целодневным
     */
    private boolean isAllDayEvent(CalendarEventData event) {
        if (event.isAllDay()) {
            return true;
        }
        // Проверка: если время начала 00:00 и время окончания 00:00 или null
        if (event.getStart() != null) {
            return event.getStart().getHour() == 0 &&
                    event.getStart().getMinute() == 0 &&
                    (event.getEnd() == null ||
                            (event.getEnd().getHour() == 0 && event.getEnd().getMinute() == 0));
        }
        return false;
    }

    /**
     * Построение ICS контента для короткой версии события
     */
    private String buildShortIcsContent(String uid, String summary, LocalDateTime start, LocalDateTime end, boolean isAllDay) {
        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\n");
        ics.append("VERSION:2.0\n");
        ics.append("PRODID:-//Double Calendar Sync//RU\n");
        ics.append("CALSCALE:GREGORIAN\n");
        ics.append("BEGIN:VEVENT\n");
        ics.append("UID:").append(uid).append("\n");

        if (isAllDay) {
            // Целодневное событие
            ics.append("DTSTART;VALUE=DATE:").append(start.format(ALL_DAY_FORMAT)).append("\n");
            if (end != null) {
                ics.append("DTEND;VALUE=DATE:").append(end.format(ALL_DAY_FORMAT)).append("\n");
            } else {
                ics.append("DTEND;VALUE=DATE:").append(start.format(ALL_DAY_FORMAT)).append("\n");
            }
        } else {
            // Обычное событие с временем
            ics.append("DTSTART:").append(start.format(DATE_TIME_FORMAT)).append("Z\n");
            if (end != null) {
                ics.append("DTEND:").append(end.format(DATE_TIME_FORMAT)).append("Z\n");
            }
        }

        ics.append("SUMMARY:").append(escapeText(summary)).append("\n");
        ics.append("TRANSP:OPAQUE\n");
        ics.append("END:VEVENT\n");
        ics.append("END:VCALENDAR\n");

        return ics.toString();
    }

    /**
     * Построение XML запроса для CalDAV
     */
    private String buildReportXml(String startStr, String endStr) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<C:calendar-query xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n" +
                "  <D:prop><C:calendar-data/></D:prop>\n" +
                "  <C:filter>\n" +
                "    <C:comp-filter name=\"VCALENDAR\">\n" +
                "      <C:comp-filter name=\"VEVENT\">\n" +
                "        <C:time-range start=\"" + startStr + "\" end=\"" + endStr + "\" />\n" +
                "      </C:comp-filter>\n" +
                "    </C:comp-filter>\n" +
                "  </C:filter>\n" +
                "</C:calendar-query>";
    }

    /**
     * Парсинг событий из ответа CalDAV
     */
    private List<CalendarEventData> parseEvents(String responseBody) {
        List<CalendarEventData> events = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return events;
        }

        Pattern veventPattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL);
        Matcher matcher = veventPattern.matcher(responseBody);

        while (matcher.find()) {
            String vevent = matcher.group(1);
            CalendarEventData event = parseVEvent(vevent);
            if (event != null) {
                events.add(event);
            }
        }

        log.debug("Распарсено {} событий", events.size());
        return events;
    }

    /**
     * Парсинг одного VEVENT
     */
    private CalendarEventData parseVEvent(String vevent) {
        try {
            String uid = extractField(vevent, "UID");
            String summary = extractField(vevent, "SUMMARY");
            String description = extractMultilineField(vevent, "DESCRIPTION");

            // Очищаем escape-последовательности
            if (description != null) {
                description = description
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\,", ",")
                        .replace("\\;", ";")
                        .replace("\\\\", "\\");
            }

            String dtstartRaw = extractField(vevent, "DTSTART");
            String dtendRaw = extractField(vevent, "DTEND");

            LocalDateTime start = parseICalDate(dtstartRaw);
            LocalDateTime end = parseICalDate(dtendRaw);

            // Проверка на целодневное событие
            boolean isAllDay = dtstartRaw != null && dtstartRaw.contains("VALUE=DATE");

            return CalendarEventData.builder()
                    .uid(uid)
                    .summary(summary)
                    .description(description)
                    .start(start)
                    .end(end)
                    .allDay(isAllDay)
                    .build();

        } catch (Exception e) {
            log.warn("Ошибка парсинга VEVENT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Извлечение поля из VEVENT
     */
    private String extractField(String content, String fieldName) {
        Pattern pattern = Pattern.compile(fieldName + "(?:;[^:]*)?:(.*?)(?:\\r?\\n|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    /**
     * Извлечение многострочного поля
     */
    private String extractMultilineField(String content, String fieldName) {
        Pattern pattern = Pattern.compile(fieldName + "(?:;[^:]*)?:(.*?)(?:\\r?\\n[A-Z]|\\Z)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            value = value.replaceAll("\\r?\\n\\s+", "");
            return value;
        }
        return "";
    }

    /**
     * Парсинг даты из iCal формата
     */
    private LocalDateTime parseICalDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            String clean = dateStr.replaceAll("[^\\dTZ]", "");

            // All-day событие: 8 цифр (YYYYMMDD)
            if (clean.length() == 8 && clean.matches("\\d{8}")) {
                return LocalDateTime.parse(clean + "T000000", DATE_TIME_FORMAT);
            }

            // Дата-время
            if (clean.length() >= 15) {
                String dateTimePart = clean.substring(0, 15);
                if (dateTimePart.matches("\\d{8}T\\d{6}")) {
                    return LocalDateTime.parse(dateTimePart, DATE_TIME_FORMAT);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка парсинга даты '{}': {}", dateStr, e.getMessage());
        }
        return null;
    }

    /**
     * Экранирование текста для ICS
     */
    private String escapeText(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}