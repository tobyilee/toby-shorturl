package com.tobyshorturl.link.repository;

import com.tobyshorturl.link.domain.Link;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
    Page<Link> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Link l SET l.clickCount = l.clickCount + 1 WHERE l.id = :linkId")
    void incrementClickCount(Long linkId);
}
