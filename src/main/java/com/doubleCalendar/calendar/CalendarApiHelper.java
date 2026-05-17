// src/test/java/com/alex/bot/base/CalendarApiHelper.java
package com.doubleCalendar.calendar;

import com.doubleCalendar.config.YandexCalendarConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Component
public class CalendarApiHelper {

    private final YandexCalendarConfig calendarConfig;
    private final RestClient calendarRestClient;

    private static final DateTimeFormatter ALL_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    public List<CalendarEventData> getAllEvents() {
        try {
            LocalDateTime now = LocalDateTime.now();
            String startStr = now.minusMonths(3)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            String endStr = now.plusMonths(3)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

            String reportBody = buildReportXml(startStr, endStr);
            String calendarUrl = getCalendarUrl();

            ResponseEntity<String> response = calendarRestClient.method(
                            HttpMethod.valueOf("REPORT"))
                    .uri(calendarUrl)
                    .headers(this::setAuthHeaders)
                    .body(reportBody)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 207) {
                return parseEvents(response.getBody());
            }
        } catch (Exception e) {
            log.error("Ошибка получения событий из календаря: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    public Optional<CalendarEventData> findEventByUid(String uid) {
        return getAllEvents().stream()
                .filter(e -> uid.equals(e.getUid()))
                .findFirst();
    }

    public boolean eventExists(String uidPart) {
        return getAllEvents().stream()
                .anyMatch(e -> e.getUid().contains(uidPart));
    }

    public void cleanCalendar() {
        List<CalendarEventData> events = getAllEvents();
        log.info("Очистка календаря: найдено {} событий", events.size());

        for (CalendarEventData event : events) {
            try {
                String deleteUrl = getCalendarUrl() + "/" + event.getUid() + ".ics";
                calendarRestClient.delete()
                        .uri(deleteUrl)
                        .headers(this::setAuthHeaders)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Удалено событие: {} - {}", event.getUid(), event.getSummary());
            } catch (Exception e) {
                log.warn("Ошибка удаления события {}: {}", event.getUid(), e.getMessage());
            }
        }

        List<CalendarEventData> remaining = getAllEvents();
        log.info("После очистки осталось событий: {}", remaining.size());
        log.info("Очистка календаря завершена");
    }

    public boolean deleteEvent(String uid) {
        try {
            String deleteUrl = getCalendarUrl() + "/" + uid + ".ics";
            calendarRestClient.delete()
                    .uri(deleteUrl)
                    .headers(this::setAuthHeaders)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Удалено событие: {}", uid);
            return true;
        } catch (Exception e) {
            log.error("Ошибка удаления события {}: {}", uid, e.getMessage());
            return false;
        }
    }

    public int countEventsOnDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        return (int) getAllEvents().stream()
                .filter(e -> e.getStart() != null)
                .filter(e -> !e.getStart().isBefore(start) && e.getStart().isBefore(end))
                .count();
    }

    private String getCalendarUrl() {
        return calendarConfig.getUrl() + "/calendars/" +
                calendarConfig.getUsername() + "/" + calendarConfig.getCalendarName();
    }

    private void setAuthHeaders(HttpHeaders headers) {
        String auth = calendarConfig.getUsername() + ":" + calendarConfig.getPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set("Depth", "1");
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

        // ✅ Ищем VEVENT блоки
        Pattern veventPattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL);
        Matcher matcher = veventPattern.matcher(responseBody);

        while (matcher.find()) {
            String vevent = matcher.group(1);
            CalendarEventData event = parseVEvent(vevent);
            if (event != null) {
                events.add(event);
            }
        }

        log.debug("Распарсено {} событий из календаря", events.size());
        return events;
    }

    private CalendarEventData parseVEvent(String vevent) {
        try {
            String uid = extractField(vevent, "UID");
            String summary = extractField(vevent, "SUMMARY");

            // ✅ Извлекаем DESCRIPTION (многострочный)
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

            // Извлекаем DTSTART и DTEND
            String dtstartRaw = extractField(vevent, "DTSTART");
            String dtendRaw = extractField(vevent, "DTEND");

            LocalDateTime start = parseICalDate(dtstartRaw);
            LocalDateTime end = parseICalDate(dtendRaw);

            log.debug("Parsed event: uid={}, summary={}, description={}",
                    uid, summary, description != null ? description.substring(0, Math.min(50, description.length())) : "null");

            return new CalendarEventData(start, end, summary, uid, description, null);
        } catch (Exception e) {
            log.warn("Ошибка парсинга VEVENT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Извлечь значение поля из VEVENT (поддерживает параметры, например DTSTART;VALUE=DATE:20260510)
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
     * ✅ Извлечь многострочное значение поля (для DESCRIPTION)
     */
    private String extractMultilineField(String content, String fieldName) {
        // Ищем поле DESCRIPTION, которое может занимать несколько строк
        // Заканчивается на следующем поле (начинающемся с буквы и двоеточия) или конце строки
        Pattern pattern = Pattern.compile(fieldName + "(?:;[^:]*)?:(.*?)(?:\\r?\\n[A-Z]|\\Z)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            // Убираем начальные пробелы на каждой строке (folded lines в iCal)
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

            log.debug("Парсинг даты: raw='{}', clean='{}'", dateStr, clean);

            // All-day событие: 8 цифр (YYYYMMDD)
            if (clean.length() == 8 && clean.matches("\\d{8}")) {
                LocalDate date = LocalDate.parse(clean, ALL_DAY_FORMAT);
                log.debug("All-day дата: {}", date);
                return date.atStartOfDay();
            }

            // Дата-время: минимум 15 символов (YYYYMMDDTHHMMSS)
            if (clean.length() >= 15) {
                String dateTimePart = clean.substring(0, 15);

                if (dateTimePart.matches("\\d{8}T\\d{6}")) {
                    LocalDateTime dateTime = LocalDateTime.parse(dateTimePart, DATE_TIME_FORMAT);

                    // Учитываем UTC (символ Z в конце)
                    if (clean.endsWith("Z")) {
                        dateTime = dateTime.plusHours(3); // MSK = UTC+3
                        log.debug("UTC дата (конвертирована в MSK): {} -> {}", dateTimePart, dateTime);
                    } else {
                        log.debug("Локальная дата: {}", dateTime);
                    }

                    return dateTime;
                }
            }

            log.warn("Не удалось распарсить дату: raw='{}', clean='{}', длина={}", dateStr, clean, clean.length());
        } catch (Exception e) {
            log.error("Ошибка парсинга даты '{}': {}", dateStr, e.getMessage(), e);
        }
        return null;
    }
}