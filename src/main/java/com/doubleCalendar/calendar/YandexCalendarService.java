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
import java.time.*;
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

    private static final DateTimeFormatter ALL_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE_TIME_UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private volatile String lastSyncInfo = null;
    private final Object syncLock = new Object();

    private static final String COPY_SUFFIX = "@double-calendar-sync";

    @PostConstruct
    public void init() {
        log.info("Инициализация YandexCalendarService...");

        // Очистка календаря 2 от старых копий
        clearCalendar2();

        // Переход на Base64-формат UID
        log.info("UID формат: Base64");
    }


    private void clearCalendar2() {
        log.info("Очистка календаря 2...");
        List<CalendarEventData> events = getAllEventsFromCalendar2Unfiltered();
        int deleted = 0;
        for (CalendarEventData event : events) {
            String uid = event.getUid();
            if (uid != null) {
                if (deleteEventFromCalendar2(uid)) {
                    deleted++;
                }
            }
        }
        log.info("Удалено {} событий из календаря 2", deleted);
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        // Убирает всё, что между // и @
        return url.replaceAll("://[^@]*@", "://*****@");
    }

    /**
     * Инициализация и проверка подключения
     */
    public boolean initialize() {
        try {
            log.info("Проверка подключения к календарям...");

            String url1 = getCalendar1Url();
            String url2 = getCalendar2Url();

            log.info("URL календаря 1: {}", maskUrl(url1));
            log.info("URL календаря 2: {}", maskUrl(url2));

            if (url1 == null || url2 == null) {
                log.error("❌ URL календарей не заданы");
                return false;
            }

            List<CalendarEventData> events1 = getAllEventsFromCalendar1();
            List<CalendarEventData> events2 = getAllEventsFromCalendar2();

            log.info("✅ Подключение успешно. Календарь 1: {} событий, Календарь 2: {} событий",
                    events1.size(), events2.size());
            return true;
        } catch (Exception e) {
            log.error("❌ Ошибка подключения: {}", e.getMessage());
            return false;
        }
    }

    private String getCalendar1Url() {
        if (config.getUrl() == null || config.getUrl().isEmpty()) return null;
        if (config.getUsername() == null || config.getUsername().isEmpty()) return null;
        if (config.getCalendarName() == null || config.getCalendarName().isEmpty()) return null;
        return buildCalendarUrl(config.getUrl(), config.getUsername(), config.getCalendarName());
    }

    private String getCalendar2Url() {
        if (calendar2Config.getUrl() == null || calendar2Config.getUrl().isEmpty()) return null;
        if (calendar2Config.getUsername() == null || calendar2Config.getUsername().isEmpty()) return null;
        if (calendar2Config.getCalendarName() == null || calendar2Config.getCalendarName().isEmpty()) return null;
        return buildCalendarUrl(calendar2Config.getUrl(), calendar2Config.getUsername(), calendar2Config.getCalendarName());
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
        synchronized (syncLock) {
            try {
                List<CalendarEventData> eventsFromCalendar1 = getAllEventsFromCalendar1();
                log.info("Получено {} событий из календаря 1", eventsFromCalendar1.size());

                List<CalendarEventData> eventsFromCalendar2 = getAllEventsFromCalendar2();
                Set<String> existingUids = new HashSet<>();
                for (CalendarEventData event : eventsFromCalendar2) {
                    if (event.getUid() != null) {
                        existingUids.add(event.getUid());
                    }
                }
                log.info("Событий в календаре 2: {}", existingUids.size());

                // Собираем оригинальные UID'ы для проверки удалений
                Set<String> originalUids = new HashSet<>();
                for (CalendarEventData event : eventsFromCalendar1) {
                    if (event.getUid() != null) {
                        originalUids.add(event.getUid());
                    }
                }

                // Фильтруем только будущие события
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

                List<CalendarEventData> futureEvents = new ArrayList<>();
                for (CalendarEventData event : eventsFromCalendar1) {
                    if (event.getStart() != null && !event.getStart().isBefore(todayStart)) {
                        futureEvents.add(event);
                    }
                }

                log.info("Будущих событий: {} (из {})", futureEvents.size(), eventsFromCalendar1.size());

                int createdCount = 0;
                int skippedCount = 0;

                log.info("existingUids в календаре 2 ({} шт): {}", existingUids.size(), existingUids);


                // Создаём копии новых событий
                for (CalendarEventData sourceEvent : futureEvents) {
                    String originalUid = sourceEvent.getUid();



                    if (originalUid == null || originalUid.isEmpty()) {
                        log.warn("Пропуск события без UID: {}", sourceEvent.getSummary());
                        continue;
                    }

                    String copyUid = generateCopyUid(originalUid);


                    log.info("Проверка копии: original={}, copy={}, existsInCalendar2={}",
                            originalUid, copyUid, existingUids.contains(copyUid));


                    if (existingUids.contains(copyUid)) {
                        log.debug("Копия уже существует: {} -> {}", originalUid, copyUid);
                        skippedCount++;
                        continue;
                    }

                    log.info("Создание копии: {} -> {} ({})", originalUid, copyUid, sourceEvent.getSummary());

                    if (createShortEventInCalendar2(sourceEvent, copyUid)) {
                        createdCount++;
                        existingUids.add(copyUid);
                        log.info("✓ Создана копия: {}", sourceEvent.getSummary());
                    } else {
                        log.error("✗ Не удалось создать копию: {}", sourceEvent.getSummary());
                    }

                    try { Thread.sleep(100); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // Удаляем копии, у которых оригинал удалён из календаря 1
                int deletedCount = 0;
                for (CalendarEventData event2 : eventsFromCalendar2) {
                    String uid2 = event2.getUid();
                    if (uid2 == null || !uid2.endsWith("-double-calendar-sync")) continue;

                    String originalUid = restoreOriginalUid(uid2);

                    if (!originalUids.contains(originalUid)) {
                        log.info("Удаление копии (оригинал удалён): {} -> оригинал {}", uid2, originalUid);
                        if (deleteEventFromCalendar2(uid2)) {
                            deletedCount++;
                            log.info("✓ Удалена копия: {}", event2.getSummary());
                        } else {
                            log.error("✗ Не удалось удалить копию: {}", event2.getSummary());
                        }
                        try { Thread.sleep(100); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                log.info("Синхронизация завершена. Создано: {}, Удалено: {}, Пропущено: {}",
                        createdCount, deletedCount, skippedCount);
                updateLastSyncInfo(createdCount, deletedCount, skippedCount);

            } catch (Exception e) {
                log.error("Ошибка при синхронизации календарей: {}", e.getMessage(), e);
            }

            log.info("=== Конец синхронизации ===");
        }
    }


    private List<CalendarEventData> getAllEventsFromCalendar2Unfiltered() {
        String url = getCalendar2Url();
        if (url == null) return new ArrayList<>();
        return fetchEventsUnfiltered(url, calendar2Config.getUsername(), calendar2Config.getPassword());
    }

    private List<CalendarEventData> fetchEventsUnfiltered(String calendarUrl, String username, String password) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Без time-range — получаем ВСЕ события
                String reportBody = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<C:calendar-query xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n" +
                        "  <D:prop><C:calendar-data/></D:prop>\n" +
                        "  <C:filter>\n" +
                        "    <C:comp-filter name=\"VCALENDAR\">\n" +
                        "      <C:comp-filter name=\"VEVENT\"/>\n" +
                        "    </C:comp-filter>\n" +
                        "  </C:filter>\n" +
                        "</C:calendar-query>";

                ResponseEntity<String> response = restClient.method(HttpMethod.valueOf("REPORT"))
                        .uri(calendarUrl)
                        .headers(h -> setReportHeaders(h, username, password))
                        .body(reportBody)
                        .retrieve()
                        .toEntity(String.class);

                if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 207) {
                    return parseEvents(response.getBody());
                }
            } catch (Exception e) {
                log.error("Ошибка получения всех событий из {} (попытка {}): {}", calendarUrl, attempt + 1, e.getMessage());
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


    /**
     * Удаляет событие из календаря 2
     */
    private boolean deleteEventFromCalendar2(String uid) {
        String calendar2Url = getCalendar2Url();
        if (calendar2Url == null) return false;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String eventUrl = calendar2Url + "/" + uid + ".ics";

                ResponseEntity<Void> response = restClient.delete()
                        .uri(eventUrl)
                        .headers(h -> setCalendarHeaders(h, calendar2Config.getUsername(), calendar2Config.getPassword()))
                        .retrieve()
                        .toBodilessEntity();

                if (response.getStatusCode().is2xxSuccessful() ||
                        response.getStatusCode() == HttpStatus.NO_CONTENT) {
                    return true;
                }
            } catch (RestClientException e) {
                log.warn("Ошибка удаления (попытка {}): {}", attempt + 1, e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Генерация UID копии на основе оригинального UID
     */

    private String generateCopyUid(String originalUid) {
        String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(originalUid.getBytes(StandardCharsets.UTF_8));
        return base64 + COPY_SUFFIX;
    }

    private String restoreOriginalUid(String copyUid) {
        if (copyUid.endsWith(COPY_SUFFIX)) {
            String base64 = copyUid.substring(0, copyUid.length() - COPY_SUFFIX.length());
            return new String(Base64.getUrlDecoder().decode(base64), StandardCharsets.UTF_8);
        }
        return copyUid;
    }

    private boolean createShortEventInCalendar2(CalendarEventData sourceEvent, String copyUid) {
        String calendar2Url = getCalendar2Url();

        if (calendar2Url == null) {
            log.error("URL календаря 2 не задан");
            return false;
        }

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String shortSummary = buildShortSummary(sourceEvent.getSummary());
                boolean isAllDay = sourceEvent.isAllDay();
                String icsContent = buildShortIcsContent(copyUid, shortSummary, sourceEvent.getStart(), sourceEvent.getEnd(), isAllDay);
                String eventUrl = calendar2Url + "/" + copyUid + ".ics";

                ResponseEntity<Void> response = restClient.put()
                        .uri(eventUrl)
                        .headers(h -> setCalendarHeaders(h, calendar2Config.getUsername(), calendar2Config.getPassword()))
                        .body(icsContent)
                        .retrieve()
                        .toBodilessEntity();

                if (response.getStatusCode().is2xxSuccessful() ||
                        response.getStatusCode() == HttpStatus.CREATED ||
                        response.getStatusCode() == HttpStatus.NO_CONTENT) {
                    return true;
                }
            } catch (RestClientException e) {
                String errorMsg = e.getMessage();
                log.warn("Ошибка создания (попытка {}): {}", attempt + 1, errorMsg);

                if (errorMsg != null && errorMsg.contains("409")) {
                    return true; // Уже существует
                }

                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }


    // Упрощенный метод без originalUid
    private String buildShortIcsContent(String uid, String summary, LocalDateTime start, LocalDateTime end, boolean isAllDay) {
        DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//Double Calendar Sync//RU\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:REQUEST\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("\r\n");
        ics.append("DTSTAMP:").append(nowUtc.format(utcFormatter)).append("\r\n");


            if (!isAllDay && start != null) {
                // Получаем системный часовой пояс
                ZoneId systemZone = ZoneId.systemDefault();

                // Конвертируем LocalDateTime в ZonedDateTime с системным TZ
                ZonedDateTime startZoned = start.atZone(systemZone);
                ZonedDateTime endZoned = end != null ? end.atZone(systemZone) : startZoned.plusHours(1);

                // Конвертируем в UTC (без жесткого кода!)
                ZonedDateTime startUtc = startZoned.withZoneSameInstant(ZoneOffset.UTC);
                ZonedDateTime endUtc = endZoned.withZoneSameInstant(ZoneOffset.UTC);

                ics.append("DTSTART:").append(startUtc.format(DATE_TIME_UTC_FORMAT)).append("\r\n");
                ics.append("DTEND:").append(endUtc.format(DATE_TIME_UTC_FORMAT)).append("\r\n");


        } else if (start != null) {
            LocalDateTime startUtc = start.minusHours(3);
            LocalDateTime endUtc = (end != null) ? end.minusHours(3) : startUtc.plusHours(1);
            ics.append("DTSTART:").append(startUtc.format(utcFormatter)).append("\r\n");
            ics.append("DTEND:").append(endUtc.format(utcFormatter)).append("\r\n");
        } else {
            LocalDate today = LocalDate.now();
            ics.append("DTSTART;VALUE=DATE:").append(today.format(ALL_DAY_FORMAT)).append("\r\n");
            ics.append("DTEND;VALUE=DATE:").append(today.plusDays(1).format(ALL_DAY_FORMAT)).append("\r\n");
        }

        ics.append("SUMMARY:").append(escapeText(summary)).append("\r\n");
        ics.append("CLASS:PUBLIC\r\n");
        ics.append("X-YANDEX-VISIBILITY:PUBLIC\r\n");
        ics.append("TRANSP:TRANSPARENT\r\n");
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");

        return ics.toString();
    }

    private String buildShortSummary(String originalSummary) {
        if (originalSummary == null || originalSummary.isEmpty()) {
            return "Без названия";
        }
        return originalSummary.trim();
    }

    public List<CalendarEventData> getAllEventsFromCalendar1() {
        String url = getCalendar1Url();
        if (url == null) return new ArrayList<>();
        return fetchEvents(url, config.getUsername(), config.getPassword(),
                config.getSyncLookbackMonths(), config.getSyncLookaheadMonths());
    }

    private List<CalendarEventData> getAllEventsFromCalendar2() {
        String url = getCalendar2Url();
        if (url == null) return new ArrayList<>();
        return fetchEvents(url, calendar2Config.getUsername(), calendar2Config.getPassword(),
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

                ResponseEntity<String> response = restClient.method(HttpMethod.valueOf("REPORT"))
                        .uri(calendarUrl)
                        .headers(h -> setReportHeaders(h, username, password))
                        .body(reportBody)
                        .retrieve()
                        .toEntity(String.class);

                if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 207) {
                    return parseEvents(response.getBody());
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
        if (responseBody == null || responseBody.isEmpty()) return events;

        Pattern veventPattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL);
        Matcher matcher = veventPattern.matcher(responseBody);

        while (matcher.find()) {
            String vevent = matcher.group(1);
            CalendarEventData event = parseVEvent(vevent);
            if (event != null && event.getUid() != null && !event.getUid().isEmpty()) {
                events.add(event);
            }
        }
        return events;
    }

    private CalendarEventData parseVEvent(String vevent) {
        try {
            String uid = extractField(vevent, "UID");
            String summary = extractField(vevent, "SUMMARY");
            String dtstartRaw = extractField(vevent, "DTSTART");
            String dtendRaw = extractField(vevent, "DTEND");

            boolean isAllDay = false;
            if (dtstartRaw != null && dtstartRaw.contains("VALUE=DATE")) {
                isAllDay = true;
            }
            if (dtstartRaw != null) {
                String cleanDate = dtstartRaw.replaceAll(".*:", "").trim();
                if (cleanDate.matches("\\d{8}") && !cleanDate.contains("T")) {
                    isAllDay = true;
                }
            }

            LocalDateTime start = parseICalDate(dtstartRaw, isAllDay);
            LocalDateTime end = parseICalDate(dtendRaw, isAllDay);

            return CalendarEventData.builder()
                    .start(start)
                    .end(end)
                    .summary(summary != null ? summary : "")
                    .uid(uid != null ? uid : "")
                    .allDay(isAllDay)
                    .build();
        } catch (Exception e) {
            log.warn("Ошибка парсинга VEVENT: {}", e.getMessage());
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
        if (dateStr == null || dateStr.isEmpty()) return null;

        try {
            String clean = dateStr;
            if (clean.contains(":")) {
                clean = clean.substring(clean.lastIndexOf(":") + 1);
            }
            clean = clean.replaceAll("[^\\dTZ]", "");

            if (isAllDay) {
                if (clean.length() >= 8) {
                    String datePart = clean.substring(0, 8);
                    if (datePart.matches("\\d{8}")) {
                        return LocalDate.parse(datePart, ALL_DAY_FORMAT).atStartOfDay();
                    }
                }
            } else {
                if (clean.length() >= 15) {
                    String dateTimePart = clean.substring(0, 15);
                    if (dateTimePart.matches("\\d{8}T\\d{6}")) {
                        LocalDateTime dateTime = LocalDateTime.parse(dateTimePart, DATE_TIME_FORMAT);
                        if (clean.endsWith("Z")) {
                            dateTime = dateTime.plusHours(3);
                        }
                        return dateTime;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка парсинга даты '{}': {}", dateStr, e.getMessage());
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

    public CalendarStats getCalendarStats() {
        try {
            List<CalendarEventData> events = getAllEventsFromCalendar1();
            LocalDateTime now = LocalDateTime.now();

            int pastCount = 0, futureCount = 0, todayCount = 0;

            for (CalendarEventData event : events) {
                if (event.getStart() != null) {
                    if (event.getStart().toLocalDate().equals(now.toLocalDate())) {
                        todayCount++;
                    } else if (event.getStart().isBefore(now)) {
                        pastCount++;
                    } else {
                        futureCount++;
                    }
                }
            }

            return CalendarStats.builder()
                    .totalEvents(events.size())
                    .pastEvents(pastCount)
                    .futureEvents(futureCount)
                    .todayEvents(todayCount)
                    .build();
        } catch (Exception e) {
            log.error("Ошибка получения статистики: {}", e.getMessage());
            return CalendarStats.builder().totalEvents(0).pastEvents(0).futureEvents(0).todayEvents(0).build();
        }
    }

    public String getLastSyncInfo() {
        return lastSyncInfo;
    }

    private void updateLastSyncInfo(int created, int deleted, int skipped) {
        lastSyncInfo = String.format("Последняя синхронизация: %s | Создано: %d, Удалено: %d, Пропущено: %d",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                created, deleted, skipped);
    }
}