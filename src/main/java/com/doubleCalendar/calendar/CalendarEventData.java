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
    private boolean allDay;
}