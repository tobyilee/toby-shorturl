package com.tobyshorturl.analytics.service;

import com.tobyshorturl.analytics.repository.ClickEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    public record DateCount(String date, long clicks) {}

    public record StatsV1(long totalClicks, long uniqueClicks, List<DateCount> clicksByDate) {}

    public record StatsV2(long totalClicks, long uniqueClicks, List<DateCount> clicksByDate,
                          Map<String, Long> topReferers, Map<String, Long> deviceBreakdown,
                          Map<String, Long> browserBreakdown) {}

    public StatsV1 getStatsV1(Long linkId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long totalClicks = clickEventRepository.countByLinkIdAndClickedAtBetween(linkId, fromInstant, toInstant);
        long uniqueClicks = clickEventRepository.countUniqueClicks(linkId, fromInstant, toInstant);
        List<DateCount> clicksByDate = clickEventRepository.countByLinkIdGroupByDate(linkId, fromInstant, toInstant)
                .stream()
                .map(row -> new DateCount(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();

        return new StatsV1(totalClicks, uniqueClicks, clicksByDate);
    }

    public StatsV2 getStatsV2(Long linkId, LocalDate from, LocalDate to) {
        StatsV1 v1 = getStatsV1(linkId, from, to);

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<String, Long> topReferers = clickEventRepository.findTopReferers(linkId, fromInstant, toInstant)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ));

        Map<String, Long> deviceBreakdown = clickEventRepository.findDeviceBreakdown(linkId, fromInstant, toInstant)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));

        Map<String, Long> browserBreakdown = clickEventRepository.findBrowserBreakdown(linkId, fromInstant, toInstant)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ));

        return new StatsV2(v1.totalClicks(), v1.uniqueClicks(), v1.clicksByDate(),
                topReferers, deviceBreakdown, browserBreakdown);
    }
}
