package com.doubleCalendar.calendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarStats {
    private int totalEvents;
    private int pastEvents;
    private int futureEvents;
    private int todayEvents;
}