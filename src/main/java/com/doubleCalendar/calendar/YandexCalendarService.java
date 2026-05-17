package com.doubleCalendar.calendar;

import com.doubleCalendar.config.Calendar2Config;
import com.doubleCalendar.config.YandexCalendarConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private static final DateTimeFormatter ALL_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE_TIME_UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    @PostConstruct
    public void init() {
        log.info("Инициализация сервиса синхронизации календарей");

        calendar1Url = buildCalendarUrl(config.getUrl(), config.getUsername(), config.getCalendarName());
        log.info("Calendar 1 URL: {}", calendar1Url);

        calendar2Url = buildCalendarUrl(calendar2Config.getUrl(), calendar2Config.getUsername(), calendar2Config.getCalendarName());
        log.info("Calendar 2 URL: {}", calendar2Url);

        syncCalendars();
    }

    private String buildCalendarUrl(String baseUrl, String username, String calendarName) {
        String url = baseUrl;
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url + "calendars/" + username + "/" + calendarName;
    }

    private void setReportHeaders(HttpHeaders headers, String username, String password) {
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set("Depth", "1");
    }

    private void setCalendarHeaders(HttpHeaders headers, String username, String password) {
        headers.setBasicAuth(username, password);
        headers.setContentType(new MediaType("text", "calendar", StandardCharsets.UTF_8));
    }

    public void syncCalendars() {
        log.info("=== Начало синхронизации календарей ===");

        try {
            List<CalendarEventData> eventsFromCalendar1 = getAllEventsFromCalendar1();
            log.info("Получено {} событий из календаря 1", eventsFromCalendar1.size());

            if (eventsFromCalendar1.isEmpty()) {
                log.info("Нет событий для синхронизации");
                return;
            }

            // Логируем все события из календаря 1
            for (CalendarEventData event : eventsFromCalendar1) {
                log.info("Календарь 1 - Событие: uid={}, summary={}, start={}, end={}, allDay={}",
                        event.getUid(), event.getSummary(), event.getStart(), event.getEnd(), event.isAllDay());
            }

            List<CalendarEventData> eventsFromCalendar2 = getAllEventsFromCalendar2();
            log.info("Получено {} событий из календаря 2", eventsFromCalendar2.size());

            // Создаём множество SUMMARY существующих событий для проверки дубликатов
            Set<String> existingSummaries = new HashSet<>();
            Map<String, String> existingUidBySummary = new HashMap<>();
            for (CalendarEventData event : eventsFromCalendar2) {
                if (event.getSummary() != null && !event.getSummary().isEmpty()) {
                    existingSummaries.add(event.getSummary());
                    existingUidBySummary.put(event.getSummary(), event.getUid());
                }
            }
            log.info("Существующих событий в календаре 2: {}", existingSummaries.size());

            int createdCount = 0;
            int skippedCount = 0;

            // ТОЛЬКО создаём новые события с НОВЫМИ UID
            for (CalendarEventData sourceEvent : eventsFromCalendar1) {
                String summary = sourceEvent.getSummary();
                if (summary == null || summary.isEmpty()) {
                    log.warn("Пропуск события без SUMMARY");
                    continue;
                }

                // Проверяем, существует ли уже событие с таким же SUMMARY в календаре 2
                if (existingSummaries.contains(summary)) {
                    log.debug("Событие уже существует в календаре 2: {}", summary);
                    skippedCount++;
                    continue;
                }

                // Создаём копию с НОВЫМ UID
                log.info("Создание копии: {} (allDay={}, start={}, end={})",
                        summary, sourceEvent.isAllDay(), sourceEvent.getStart(), sourceEvent.getEnd());

                if (createShortEventInCalendar2(sourceEvent)) {
                    createdCount++;
                    log.info("✓ Создано: {}", summary);
                } else {
                    log.error("✗ Не удалось создать: {}", summary);
                }

                Thread.sleep(100);
            }

            log.info("Синхронизация завершена. Создано: {}, Пропущено (уже существуют): {}",
                    createdCount, skippedCount);

        } catch (Exception e) {
            log.error("Ошибка при синхронизации календарей: {}", e.getMessage(), e);
        }

        log.info("=== Конец синхронизации ===");
    }

    private boolean createShortEventInCalendar2(CalendarEventData sourceEvent) {
        // Генерируем НОВЫЙ уникальный UID для копии
        String newUid = UUID.randomUUID().toString() + "@double-calendar-sync";

        log.info("Создание копии с НОВЫМ UID: {} (оригинальный: {})", newUid, sourceEvent.getUid());

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String shortSummary = buildShortSummary(sourceEvent.getSummary());
                boolean isAllDay = sourceEvent.isAllDay();

                String icsContent = buildShortIcsContent(newUid, shortSummary, sourceEvent.getStart(), sourceEvent.getEnd(), isAllDay);
                String eventUrl = calendar2Url + "/" + newUid + ".ics";

                log.debug("PUT {} \n{}", eventUrl, icsContent);

                ResponseEntity<Void> response = restClient.put()
                        .uri(eventUrl)
                        .headers(h -> setCalendarHeaders(h, calendar2Config.getUsername(), calendar2Config.getPassword()))
                        .body(icsContent)
                        .retrieve()
                        .toBodilessEntity();

                if (response.getStatusCode().is2xxSuccessful() ||
                        response.getStatusCode() == HttpStatus.CREATED ||
                        response.getStatusCode() == HttpStatus.NO_CONTENT) {
                    log.info("✓ Копия создана (попытка {}): {} (новый UID: {})", attempt + 1, shortSummary, newUid);
                    return true;
                } else {
                    log.warn("Неожиданный статус при создании (попытка {}): {}", attempt + 1, response.getStatusCode());
                }

            } catch (RestClientException e) {
                log.warn("Ошибка создания (попытка {}): {}", attempt + 1, e.getMessage());

                if (attempt < MAX_RETRIES - 1) {
                    try {
                        long delay = RETRY_DELAY_MS * (attempt + 1);
                        log.info("Повторная попытка через {} мс...", delay);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }




    private String buildShortSummary(String originalSummary) {
        if (originalSummary == null || originalSummary.isEmpty()) {
            return "Без названия";
        }
        return originalSummary.trim();
    }

    public List<CalendarEventData> getAllEventsFromCalendar1() {
        return fetchEvents(calendar1Url, config.getUsername(), config.getPassword(),
                config.getSyncLookbackMonths(), config.getSyncLookaheadMonths());
    }

    private List<CalendarEventData> getAllEventsFromCalendar2() {
        return fetchEvents(calendar2Url, calendar2Config.getUsername(), calendar2Config.getPassword(),
                config.getSyncLookbackMonths(), config.getSyncLookaheadMonths());
    }

    private List<CalendarEventData> fetchEvents(String calendarUrl, String username, String password,
                                                int lookbackMonths, int lookaheadMonths) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
                String startStr = now.minusMonths(lookbackMonths).format(DATE_TIME_UTC_FORMAT);
                String endStr = now.plusMonths(lookaheadMonths).format(DATE_TIME_UTC_FORMAT);

                String reportBody = buildReportXml(startStr, endStr);

                log.debug("Запрос событий с {} по {} (попытка {})", startStr, endStr, attempt + 1);

                ResponseEntity<String> response = restClient.method(HttpMethod.valueOf("REPORT"))
                        .uri(calendarUrl)
                        .headers(h -> setReportHeaders(h, username, password))
                        .body(reportBody)
                        .retrieve()
                        .toEntity(String.class);

                if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 207) {
                    List<CalendarEventData> events = parseEvents(response.getBody());
                    log.debug("Получено {} событий из {}", events.size(), calendarUrl);
                    return events;
                } else {
                    log.warn("Неожиданный статус ответа: {} (попытка {})", response.getStatusCode(), attempt + 1);
                }
            } catch (Exception e) {
                log.error("Ошибка получения событий из {} (попытка {}): {}", calendarUrl, attempt + 1, e.getMessage());

                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return new ArrayList<>();
    }


    private String buildShortIcsContent(String uid, String summary, LocalDateTime start, LocalDateTime end, boolean isAllDay) {
        DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//Double Calendar Sync//RU\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("\r\n");
        ics.append("DTSTAMP:").append(nowUtc.format(utcFormatter)).append("\r\n");

        if (isAllDay && start != null) {
            // Цельнодневное событие
            LocalDate startDate = start.toLocalDate();
            ics.append("DTSTART;VALUE=DATE:").append(startDate.format(ALL_DAY_FORMAT)).append("\r\n");

            if (end != null) {
                LocalDate endDate = end.toLocalDate();
                // В iCal для цельнодневных событий DTEND - это следующий день после окончания
                ics.append("DTEND;VALUE=DATE:").append(endDate.format(ALL_DAY_FORMAT)).append("\r\n");
            } else {
                // Если нет даты окончания, добавляем следующий день
                ics.append("DTEND;VALUE=DATE:").append(startDate.plusDays(1).format(ALL_DAY_FORMAT)).append("\r\n");
            }

            log.debug("Цельнодневное событие: {} - {}", startDate, end != null ? end.toLocalDate() : startDate.plusDays(1));

        } else if (start != null) {
            // Обычное событие с временем
            LocalDateTime startUtc = start.minusHours(3);
            LocalDateTime endUtc;
            if (end != null) {
                endUtc = end.minusHours(3);
            } else {
                endUtc = startUtc.plusHours(1);
            }

            ics.append("DTSTART:").append(startUtc.format(utcFormatter)).append("\r\n");
            ics.append("DTEND:").append(endUtc.format(utcFormatter)).append("\r\n");

            log.debug("Событие с временем: {} - {}", startUtc, endUtc);

        } else {
            // Защита: если даты нет, создаём на сегодня
            log.warn("Нет даты для события {}. Использую сегодняшнюю дату.", uid);
            LocalDate today = LocalDate.now();
            ics.append("DTSTART;VALUE=DATE:").append(today.format(ALL_DAY_FORMAT)).append("\r\n");
            ics.append("DTEND;VALUE=DATE:").append(today.plusDays(1).format(ALL_DAY_FORMAT)).append("\r\n");
        }

        ics.append("SUMMARY:").append(escapeText(summary)).append("\r\n");
        ics.append("TRANSP:TRANSPARENT\r\n");
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");

        return ics.toString();
    }

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
            if (event != null && event.getUid() != null && !event.getUid().isEmpty()) {
                events.add(event);
            }
        }

        log.debug("Распарсено {} событий", events.size());
        return events;
    }

    private CalendarEventData parseVEvent(String vevent) {
        try {
            String uid = extractField(vevent, "UID");
            String summary = extractField(vevent, "SUMMARY");

            String dtstartRaw = extractField(vevent, "DTSTART");
            String dtendRaw = extractField(vevent, "DTEND");

            log.debug("Парсинг VEVENT: uid={}, summary={}, DTSTART={}, DTEND={}",
                    uid, summary, dtstartRaw, dtendRaw);

            boolean isAllDay = false;

            // Проверяем, является ли событие цельнодневным
            if (dtstartRaw != null && dtstartRaw.contains("VALUE=DATE")) {
                isAllDay = true;
            }

            // Также проверяем по формату даты (8 цифр без T)
            if (dtstartRaw != null) {
                String cleanDate = dtstartRaw.replaceAll(".*:", "").trim();
                if (cleanDate.matches("\\d{8}") && !cleanDate.contains("T")) {
                    isAllDay = true;
                }
            }

            LocalDateTime start = parseICalDate(dtstartRaw, isAllDay);
            LocalDateTime end = parseICalDate(dtendRaw, isAllDay);

            log.debug("Распарсено: start={}, end={}, allDay={}", start, end, isAllDay);

            return CalendarEventData.builder()
                    .start(start)
                    .end(end)
                    .summary(summary != null ? summary : "")
                    .uid(uid != null ? uid : "")
                    .allDay(isAllDay)
                    .build();

        } catch (Exception e) {
            log.warn("Ошибка парсинга VEVENT: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractField(String content, String fieldName) {
        Pattern pattern = Pattern.compile(fieldName + "(?:;[^:]*)?:(.*?)(?:\\r?\\n|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private LocalDateTime parseICalDate(String dateStr, boolean isAllDay) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            // Извлекаем чистую дату (после последнего двоеточия)
            String clean = dateStr;
            if (clean.contains(":")) {
                clean = clean.substring(clean.lastIndexOf(":") + 1);
            }

            // Удаляем все не-цифры, кроме T и Z
            clean = clean.replaceAll("[^\\dTZ]", "");

            log.debug("Парсинг даты: raw='{}', clean='{}', allDay={}", dateStr, clean, isAllDay);

            if (isAllDay) {
                // Цельнодневное событие: YYYYMMDD
                if (clean.length() >= 8) {
                    String datePart = clean.substring(0, 8);
                    if (datePart.matches("\\d{8}")) {
                        LocalDate date = LocalDate.parse(datePart, ALL_DAY_FORMAT);
                        log.debug("Цельнодневная дата: {}", date);
                        return date.atStartOfDay();
                    }
                }
            } else {
                // Событие с временем: YYYYMMDDTHHMMSS(Z)
                if (clean.length() >= 15) {
                    String dateTimePart = clean.substring(0, 15);
                    if (dateTimePart.matches("\\d{8}T\\d{6}")) {
                        LocalDateTime dateTime = LocalDateTime.parse(dateTimePart, DATE_TIME_FORMAT);

                        // Если время в UTC (есть Z), конвертируем в MSK (UTC+3)
                        if (clean.endsWith("Z")) {
                            dateTime = dateTime.plusHours(3);
                            log.debug("UTC -> MSK: {} -> {}", dateTimePart, dateTime);
                        } else {
                            log.debug("Локальное время: {}", dateTime);
                        }

                        return dateTime;
                    }
                }
            }

            log.warn("Не удалось распарсить дату: raw='{}', clean='{}', длина={}", dateStr, clean, clean.length());
        } catch (Exception e) {
            log.error("Ошибка парсинга даты '{}': {}", dateStr, e.getMessage(), e);
        }
        return null;
    }

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