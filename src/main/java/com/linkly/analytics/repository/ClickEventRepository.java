package com.linkly.analytics.repository;

import com.linkly.analytics.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    long countByLinkIdAndClickedAtBetween(Long linkId, Instant from, Instant to);

    @Query("""
        SELECT CAST(ce.clickedAt AS DATE) as date, COUNT(ce) as clicks
        FROM ClickEvent ce
        WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to
        GROUP BY CAST(ce.clickedAt AS DATE)
        ORDER BY date
        """)
    List<Object[]> countByLinkIdGroupByDate(Long linkId, Instant from, Instant to);

    @Query("""
        SELECT COUNT(DISTINCT CONCAT(ce.ipHash, ce.userAgent, CAST(ce.clickedAt AS DATE)))
        FROM ClickEvent ce
        WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to
        """)
    long countUniqueClicks(Long linkId, Instant from, Instant to);

    @Query("SELECT ce.referer, COUNT(ce) FROM ClickEvent ce WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to AND ce.referer IS NOT NULL GROUP BY ce.referer ORDER BY COUNT(ce) DESC")
    List<Object[]> findTopReferers(Long linkId, Instant from, Instant to);

    @Query("SELECT ce.deviceType, COUNT(ce) FROM ClickEvent ce WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to AND ce.deviceType IS NOT NULL GROUP BY ce.deviceType")
    List<Object[]> findDeviceBreakdown(Long linkId, Instant from, Instant to);

    @Query("SELECT ce.browser, COUNT(ce) FROM ClickEvent ce WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to AND ce.browser IS NOT NULL GROUP BY ce.browser ORDER BY COUNT(ce) DESC")
    List<Object[]> findBrowserBreakdown(Long linkId, Instant from, Instant to);

    List<ClickEvent> findTop10ByLinkIdOrderByClickedAtDesc(Long linkId);
}
