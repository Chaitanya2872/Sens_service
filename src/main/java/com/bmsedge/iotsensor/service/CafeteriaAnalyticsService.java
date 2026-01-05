package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.CafeteriaQueueDTO;
import com.bmsedge.iotsensor.model.CafeteriaQueue;
import com.bmsedge.iotsensor.repository.CafeteriaQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CafeteriaAnalyticsService {

    private final CafeteriaQueueRepository queueRepository;

    // Meal session definitions
    private static final LocalTime BREAKFAST_START = LocalTime.of(7, 0);
    private static final LocalTime BREAKFAST_END = LocalTime.of(10, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(15, 0);
    private static final LocalTime DINNER_START = LocalTime.of(19, 0);
    private static final LocalTime DINNER_END = LocalTime.of(22, 0);

    /**
     * Get smart insights for a specific date
     * Returns peak hours, footfall status, throughput analysis, and occupancy
     */
    public Map<String, Object> getSmartInsights(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        List<CafeteriaQueue> records = queueRepository.findByDate(startOfDay, endOfDay);

        if (records.isEmpty()) {
            return createEmptyInsights(date);
        }

        // Group by hour for analysis
        Map<Integer, List<CafeteriaQueue>> byHour = records.stream()
                .collect(Collectors.groupingBy(r -> r.getTimestamp().getHour()));

        // Calculate peak hour
        Map<String, Object> peakHour = calculatePeakHour(byHour);

        // Determine footfall status
        String footfallStatus = determineFootfallStatus(records);

        // Calculate throughput metrics
        Map<String, Object> throughput = calculateThroughput(records);

        // Calculate occupancy
        Map<String, Object> occupancy = calculateOccupancy(records);

        // Per-counter analysis
        Map<String, Object> counterAnalysis = analyzeCounters(records);

        Map<String, Object> insights = new HashMap<>();
        insights.put("date", date.toString());
        insights.put("currentHour", LocalDateTime.now().getHour());
        insights.put("totalRecords", records.size());
        insights.put("peakHour", peakHour);
        insights.put("footfallStatus", footfallStatus);
        insights.put("throughput", throughput);
        insights.put("occupancy", occupancy);
        insights.put("counters", counterAnalysis);

        return insights;
    }

    /**
     * Get wait time trend analysis
     */
    public Map<String, Object> getWaitTimeTrend(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<CafeteriaQueue> records = queueRepository.findByDateRange(start, end);

        if (records.isEmpty()) {
            return createEmptyTrend(startDate, endDate);
        }

        // Hourly analysis
        List<Map<String, Object>> hourlyTrend = calculateHourlyWaitTimeTrend(records);

        // Daily analysis
        List<Map<String, Object>> dailyTrend = calculateDailyWaitTimeTrend(records);

        // Session-based analysis
        Map<String, Object> sessionAnalysis = calculateSessionWaitTime(records);

        Map<String, Object> result = new HashMap<>();
        result.put("period", startDate + " to " + endDate);
        result.put("hourly", hourlyTrend);
        result.put("daily", dailyTrend);
        result.put("sessions", sessionAnalysis);

        return result;
    }

    /**
     * Get wait time breakdown by meal session for a specific date
     */
    public Map<String, Object> getWaitTimeBySession(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        List<CafeteriaQueue> records = queueRepository.findByDate(startOfDay, endOfDay);

        // Group by meal session
        Map<String, List<CafeteriaQueue>> bySession = new HashMap<>();
        bySession.put("breakfast", new ArrayList<>());
        bySession.put("lunch", new ArrayList<>());
        bySession.put("dinner", new ArrayList<>());
        bySession.put("other", new ArrayList<>());

        for (CafeteriaQueue record : records) {
            String session = getMealSession(record.getTimestamp());
            bySession.get(session).add(record);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());

        for (Map.Entry<String, List<CafeteriaQueue>> entry : bySession.entrySet()) {
            String session = entry.getKey();
            List<CafeteriaQueue> sessionRecords = entry.getValue();

            if (sessionRecords.isEmpty()) {
                result.put(session, createEmptySessionStats());
                continue;
            }

            Map<String, Object> stats = new HashMap<>();
            stats.put("recordCount", sessionRecords.size());
            stats.put("avgWaitTime", sessionRecords.stream()
                    .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                    .average().orElse(0.0));
            stats.put("maxWaitTime", sessionRecords.stream()
                    .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                    .max().orElse(0.0));
            stats.put("avgQueueLength", sessionRecords.stream()
                    .mapToInt(CafeteriaQueue::getQueueCount)
                    .average().orElse(0.0));
            stats.put("peakTime", findPeakTimeInSession(sessionRecords));

            result.put(session, stats);
        }

        return result;
    }

    /**
     * Get traffic pattern analysis
     */
    public Map<String, Object> getTrafficPattern(int days) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        List<CafeteriaQueue> records = queueRepository.findByDateRange(startDate, endDate);

        // Weekday vs Weekend analysis
        Map<String, List<CafeteriaQueue>> byDayType = records.stream()
                .collect(Collectors.groupingBy(r -> isWeekend(r.getTimestamp()) ? "weekend" : "weekday"));

        Map<String, Object> dayTypeAnalysis = new HashMap<>();
        for (Map.Entry<String, List<CafeteriaQueue>> entry : byDayType.entrySet()) {
            dayTypeAnalysis.put(entry.getKey(), calculateTrafficStats(entry.getValue()));
        }

        // Daily peak detection
        Map<LocalDate, List<CafeteriaQueue>> byDate = records.stream()
                .collect(Collectors.groupingBy(r -> r.getTimestamp().toLocalDate()));

        List<Map<String, Object>> peakDays = byDate.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> dayData = new HashMap<>();
                    dayData.put("date", entry.getKey().toString());
                    dayData.put("dayOfWeek", entry.getKey().getDayOfWeek().toString());
                    dayData.put("stats", calculateTrafficStats(entry.getValue()));
                    return dayData;
                })
                .sorted((a, b) -> {
                    double avgA = ((Map<String, Object>) a.get("stats")).get("avgQueueLength") != null
                            ? (Double) ((Map<String, Object>) a.get("stats")).get("avgQueueLength") : 0.0;
                    double avgB = ((Map<String, Object>) b.get("stats")).get("avgQueueLength") != null
                            ? (Double) ((Map<String, Object>) b.get("stats")).get("avgQueueLength") : 0.0;
                    return Double.compare(avgB, avgA);
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("period", startDate.toLocalDate() + " to " + endDate.toLocalDate());
        result.put("totalDays", days);
        result.put("dayTypeAnalysis", dayTypeAnalysis);
        result.put("peakDays", peakDays);
        result.put("overallStats", calculateTrafficStats(records));

        return result;
    }

    /**
     * Get weekly summary for all counters
     */
    public Map<String, Object> getWeeklySummary(int weeks) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusWeeks(weeks);

        List<CafeteriaQueue> records = queueRepository.findByDateRange(startDate, endDate);

        // Group by week and counter
        Map<String, Map<String, List<CafeteriaQueue>>> byWeekAndCounter = new HashMap<>();

        for (CafeteriaQueue record : records) {
            String weekKey = getWeekKey(record.getTimestamp());
            String counter = record.getCounterName();

            byWeekAndCounter.putIfAbsent(weekKey, new HashMap<>());
            byWeekAndCounter.get(weekKey).putIfAbsent(counter, new ArrayList<>());
            byWeekAndCounter.get(weekKey).get(counter).add(record);
        }

        List<Map<String, Object>> weeklySummaries = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<CafeteriaQueue>>> weekEntry : byWeekAndCounter.entrySet()) {
            Map<String, Object> weekSummary = new HashMap<>();
            weekSummary.put("week", weekEntry.getKey());

            Map<String, Object> counterStats = new HashMap<>();
            for (Map.Entry<String, List<CafeteriaQueue>> counterEntry : weekEntry.getValue().entrySet()) {
                counterStats.put(counterEntry.getKey(), calculateTrafficStats(counterEntry.getValue()));
            }

            weekSummary.put("counters", counterStats);
            weekSummary.put("overallStats", calculateTrafficStats(
                    weekEntry.getValue().values().stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList())
            ));

            weeklySummaries.add(weekSummary);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("period", startDate.toLocalDate() + " to " + endDate.toLocalDate());
        result.put("weeks", weeks);
        result.put("weeklySummaries", weeklySummaries);

        return result;
    }

    // Helper methods

    private Map<String, Object> calculatePeakHour(Map<Integer, List<CafeteriaQueue>> byHour) {
        int peakHour = -1;
        double maxAvgQueue = 0;

        for (Map.Entry<Integer, List<CafeteriaQueue>> entry : byHour.entrySet()) {
            double avgQueue = entry.getValue().stream()
                    .mapToInt(CafeteriaQueue::getQueueCount)
                    .average()
                    .orElse(0);

            if (avgQueue > maxAvgQueue) {
                maxAvgQueue = avgQueue;
                peakHour = entry.getKey();
            }
        }

        Map<String, Object> peak = new HashMap<>();
        if (peakHour >= 0 && byHour.containsKey(peakHour)) {
            List<CafeteriaQueue> peakRecords = byHour.get(peakHour);
            peak.put("hour", peakHour);
            peak.put("hourFormatted", String.format("%02d:00", peakHour));
            peak.put("avgQueue", maxAvgQueue);
            peak.put("avgWaitTime", peakRecords.stream()
                    .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                    .average().orElse(0.0));
            peak.put("maxQueue", peakRecords.stream()
                    .mapToInt(CafeteriaQueue::getQueueCount)
                    .max().orElse(0));
        }

        return peak;
    }

    private String determineFootfallStatus(List<CafeteriaQueue> records) {
        double avgQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .average()
                .orElse(0);

        // Calculate percentiles
        List<Integer> queueCounts = records.stream()
                .map(CafeteriaQueue::getQueueCount)
                .sorted()
                .collect(Collectors.toList());

        if (queueCounts.isEmpty()) return "UNKNOWN";

        int p25Index = (int) (queueCounts.size() * 0.25);
        int p75Index = (int) (queueCounts.size() * 0.75);

        double p25 = queueCounts.get(Math.max(0, p25Index));
        double p75 = queueCounts.get(Math.min(queueCounts.size() - 1, p75Index));

        if (avgQueue > p75) return "HIGH";
        if (avgQueue < p25) return "LOW";
        return "NORMAL";
    }

    private Map<String, Object> calculateThroughput(List<CafeteriaQueue> records) {
        Map<String, Object> throughput = new HashMap<>();

        if (records.size() < 2) {
            throughput.put("avgRate", 0.0);
            throughput.put("trend", "INSUFFICIENT_DATA");
            return throughput;
        }

        // Sort by timestamp
        List<CafeteriaQueue> sorted = records.stream()
                .sorted(Comparator.comparing(CafeteriaQueue::getTimestamp))
                .collect(Collectors.toList());

        // Calculate throughput rate (people served per minute)
        double totalThroughput = 0;
        int validPairs = 0;

        for (int i = 1; i < sorted.size(); i++) {
            CafeteriaQueue current = sorted.get(i);
            CafeteriaQueue previous = sorted.get(i - 1);

            if (current.getCounterName().equals(previous.getCounterName())) {
                long minutesDiff = java.time.Duration.between(
                        previous.getTimestamp(), current.getTimestamp()).toMinutes();

                if (minutesDiff > 0 && minutesDiff <= 5) { // Only consider intervals <= 5 min
                    int queueReduction = previous.getQueueCount() - current.getQueueCount();
                    if (queueReduction > 0) {
                        double rate = queueReduction / (double) minutesDiff;
                        totalThroughput += rate;
                        validPairs++;
                    }
                }
            }
        }

        double avgRate = validPairs > 0 ? totalThroughput / validPairs : 0.0;
        throughput.put("avgRate", Math.round(avgRate * 100.0) / 100.0);

        // Determine trend (compare first half vs second half)
        int midpoint = sorted.size() / 2;
        List<CafeteriaQueue> firstHalf = sorted.subList(0, midpoint);
        List<CafeteriaQueue> secondHalf = sorted.subList(midpoint, sorted.size());

        double firstAvg = firstHalf.stream().mapToInt(CafeteriaQueue::getQueueCount).average().orElse(0);
        double secondAvg = secondHalf.stream().mapToInt(CafeteriaQueue::getQueueCount).average().orElse(0);

        String trend;
        if (secondAvg > firstAvg * 1.1) trend = "INCREASING";
        else if (secondAvg < firstAvg * 0.9) trend = "DECREASING";
        else trend = "STABLE";

        throughput.put("trend", trend);

        return throughput;
    }

    private Map<String, Object> calculateOccupancy(List<CafeteriaQueue> records) {
        Map<String, Object> occupancy = new HashMap<>();

        int totalQueue = records.stream().mapToInt(CafeteriaQueue::getQueueCount).sum();

        // Estimate total people (queue + being served)
        // Assuming 2-3 people being served per counter on average
        int estimatedServing = (int) (records.stream()
                .map(CafeteriaQueue::getCounterName)
                .distinct()
                .count() * 2.5);

        int totalPeople = totalQueue + estimatedServing;

        occupancy.put("totalQueue", totalQueue);
        occupancy.put("totalPeople", totalPeople);
        occupancy.put("estimatedServing", estimatedServing);

        if (totalPeople > 0) {
            occupancy.put("occupancyRate", Math.round(((double) totalQueue / totalPeople) * 100.0 * 100.0) / 100.0);
        } else {
            occupancy.put("occupancyRate", 0.0);
        }

        return occupancy;
    }

    private Map<String, Object> analyzeCounters(List<CafeteriaQueue> records) {
        Map<String, List<CafeteriaQueue>> byCounter = records.stream()
                .collect(Collectors.groupingBy(CafeteriaQueue::getCounterName));

        Map<String, Object> analysis = new HashMap<>();

        for (Map.Entry<String, List<CafeteriaQueue>> entry : byCounter.entrySet()) {
            String counterName = entry.getKey();
            List<CafeteriaQueue> counterRecords = entry.getValue();

            Map<String, Object> stats = new HashMap<>();
            stats.put("avgQueue", counterRecords.stream()
                    .mapToInt(CafeteriaQueue::getQueueCount)
                    .average().orElse(0.0));
            stats.put("avgWaitTime", counterRecords.stream()
                    .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                    .average().orElse(0.0));
            stats.put("throughput", calculateCounterThroughput(counterRecords));
            stats.put("status", determineCounterStatus(counterRecords));

            analysis.put(counterName, stats);
        }

        return analysis;
    }

    private double calculateCounterThroughput(List<CafeteriaQueue> records) {
        if (records.size() < 2) return 0.0;

        List<CafeteriaQueue> sorted = records.stream()
                .sorted(Comparator.comparing(CafeteriaQueue::getTimestamp))
                .collect(Collectors.toList());

        double totalRate = 0;
        int validPairs = 0;

        for (int i = 1; i < sorted.size(); i++) {
            CafeteriaQueue current = sorted.get(i);
            CafeteriaQueue previous = sorted.get(i - 1);

            long minutesDiff = java.time.Duration.between(
                    previous.getTimestamp(), current.getTimestamp()).toMinutes();

            if (minutesDiff > 0 && minutesDiff <= 5) {
                int queueReduction = previous.getQueueCount() - current.getQueueCount();
                if (queueReduction > 0) {
                    totalRate += queueReduction / (double) minutesDiff;
                    validPairs++;
                }
            }
        }

        return validPairs > 0 ? Math.round((totalRate / validPairs) * 100.0) / 100.0 : 0.0;
    }

    private String determineCounterStatus(List<CafeteriaQueue> records) {
        double avgQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .average().orElse(0);

        if (avgQueue > 15) return "BUSY";
        if (avgQueue < 5) return "NORMAL";
        return "MODERATE";
    }

    private List<Map<String, Object>> calculateHourlyWaitTimeTrend(List<CafeteriaQueue> records) {
        Map<Integer, List<CafeteriaQueue>> byHour = records.stream()
                .collect(Collectors.groupingBy(r -> r.getTimestamp().getHour()));

        List<Map<String, Object>> hourlyData = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            if (byHour.containsKey(hour)) {
                List<CafeteriaQueue> hourRecords = byHour.get(hour);
                Map<String, Object> hourStats = new HashMap<>();
                hourStats.put("hour", hour);
                hourStats.put("hourFormatted", String.format("%02d:00", hour));
                hourStats.put("avgWait", hourRecords.stream()
                        .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                        .average().orElse(0.0));
                hourStats.put("maxWait", hourRecords.stream()
                        .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                        .max().orElse(0.0));
                hourStats.put("minWait", hourRecords.stream()
                        .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                        .min().orElse(0.0));
                hourStats.put("recordCount", hourRecords.size());

                hourlyData.add(hourStats);
            }
        }

        return hourlyData;
    }

    private List<Map<String, Object>> calculateDailyWaitTimeTrend(List<CafeteriaQueue> records) {
        Map<LocalDate, List<CafeteriaQueue>> byDate = records.stream()
                .collect(Collectors.groupingBy(r -> r.getTimestamp().toLocalDate()));

        return byDate.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> dayStats = new HashMap<>();
                    List<CafeteriaQueue> dayRecords = entry.getValue();

                    dayStats.put("date", entry.getKey().toString());
                    dayStats.put("dayOfWeek", entry.getKey().getDayOfWeek().toString());
                    dayStats.put("avgWait", dayRecords.stream()
                            .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                            .average().orElse(0.0));
                    dayStats.put("peakWait", dayRecords.stream()
                            .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                            .max().orElse(0.0));

                    // Session breakdown
                    Map<String, Object> sessions = new HashMap<>();
                    for (String session : Arrays.asList("breakfast", "lunch", "dinner")) {
                        List<CafeteriaQueue> sessionRecords = dayRecords.stream()
                                .filter(r -> getMealSession(r.getTimestamp()).equals(session))
                                .collect(Collectors.toList());

                        if (!sessionRecords.isEmpty()) {
                            Map<String, Object> sessionStats = new HashMap<>();
                            sessionStats.put("avgWait", sessionRecords.stream()
                                    .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                                    .average().orElse(0.0));
                            sessionStats.put("maxWait", sessionRecords.stream()
                                    .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                                    .max().orElse(0.0));
                            sessions.put(session, sessionStats);
                        }
                    }
                    dayStats.put("sessions", sessions);

                    return dayStats;
                })
                .sorted((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")))
                .collect(Collectors.toList());
    }

    private Map<String, Object> calculateSessionWaitTime(List<CafeteriaQueue> records) {
        Map<String, List<CafeteriaQueue>> bySession = records.stream()
                .collect(Collectors.groupingBy(r -> getMealSession(r.getTimestamp())));

        Map<String, Object> sessionAnalysis = new HashMap<>();

        for (String session : Arrays.asList("breakfast", "lunch", "dinner")) {
            if (bySession.containsKey(session)) {
                List<CafeteriaQueue> sessionRecords = bySession.get(session);

                // Find peak day for this session
                Map<LocalDate, List<CafeteriaQueue>> byDate = sessionRecords.stream()
                        .collect(Collectors.groupingBy(r -> r.getTimestamp().toLocalDate()));

                LocalDate peakDay = byDate.entrySet().stream()
                        .max(Comparator.comparing(e -> e.getValue().stream()
                                .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                                .average().orElse(0.0)))
                        .map(Map.Entry::getKey)
                        .orElse(null);

                Map<String, Object> stats = new HashMap<>();
                stats.put("avgWait", sessionRecords.stream()
                        .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                        .average().orElse(0.0));
                stats.put("peakDay", peakDay != null ? peakDay.toString() : null);
                stats.put("recordCount", sessionRecords.size());

                sessionAnalysis.put(session, stats);
            } else {
                sessionAnalysis.put(session, createEmptySessionStats());
            }
        }

        return sessionAnalysis;
    }

    private Map<String, Object> calculateTrafficStats(List<CafeteriaQueue> records) {
        Map<String, Object> stats = new HashMap<>();

        if (records.isEmpty()) {
            stats.put("avgQueueLength", 0.0);
            stats.put("maxQueueLength", 0);
            stats.put("avgWaitTime", 0.0);
            stats.put("totalRecords", 0);
            return stats;
        }

        stats.put("avgQueueLength", records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .average().orElse(0.0));
        stats.put("maxQueueLength", records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .max().orElse(0));
        stats.put("avgWaitTime", records.stream()
                .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                .average().orElse(0.0));
        stats.put("totalRecords", records.size());

        return stats;
    }

    private String getMealSession(LocalDateTime timestamp) {
        LocalTime time = timestamp.toLocalTime();

        if (!time.isBefore(BREAKFAST_START) && time.isBefore(BREAKFAST_END)) {
            return "breakfast";
        } else if (!time.isBefore(LUNCH_START) && time.isBefore(LUNCH_END)) {
            return "lunch";
        } else if (!time.isBefore(DINNER_START) && time.isBefore(DINNER_END)) {
            return "dinner";
        }
        return "other";
    }

    private String findPeakTimeInSession(List<CafeteriaQueue> sessionRecords) {
        return sessionRecords.stream()
                .max(Comparator.comparing(CafeteriaQueue::getQueueCount))
                .map(r -> r.getTimestamp().toLocalTime().toString())
                .orElse("N/A");
    }

    private boolean isWeekend(LocalDateTime timestamp) {
        DayOfWeek day = timestamp.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private String getWeekKey(LocalDateTime timestamp) {
        // Get the week number and year
        int weekOfYear = timestamp.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = timestamp.getYear();
        return String.format("%d-W%02d", year, weekOfYear);
    }

    private Map<String, Object> createEmptyInsights(LocalDate date) {
        Map<String, Object> empty = new HashMap<>();
        empty.put("date", date.toString());
        empty.put("message", "No data available for this date");
        return empty;
    }

    private Map<String, Object> createEmptyTrend(LocalDate start, LocalDate end) {
        Map<String, Object> empty = new HashMap<>();
        empty.put("period", start + " to " + end);
        empty.put("message", "No data available for this period");
        empty.put("hourly", new ArrayList<>());
        empty.put("daily", new ArrayList<>());
        return empty;
    }

    private Map<String, Object> createEmptySessionStats() {
        Map<String, Object> empty = new HashMap<>();
        empty.put("avgWait", 0.0);
        empty.put("recordCount", 0);
        empty.put("message", "No data for this session");
        return empty;
    }
}