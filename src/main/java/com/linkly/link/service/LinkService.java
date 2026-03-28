package com.linkly.link.service;

import com.linkly.link.domain.Link;
import com.linkly.link.repository.LinkRepository;
import com.linkly.link.shortcode.ShortCodeGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class LinkService {

    private static final int MAX_RETRIES = 10;

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator shortCodeGenerator;

    public LinkService(LinkRepository linkRepository, ShortCodeGenerator shortCodeGenerator) {
        this.linkRepository = linkRepository;
        this.shortCodeGenerator = shortCodeGenerator;
    }

    @Transactional
    public Link createLink(String originalUrl, String title, Long userId, Instant expiresAt) {
        String shortCode = generateUniqueShortCode();
        Link link = new Link(shortCode, originalUrl, title, userId, expiresAt);
        return linkRepository.save(link);
    }

    public Optional<Link> findAccessibleByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode)
                .filter(Link::isAccessible);
    }

    public Optional<Link> findByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode);
    }

    public Optional<Link> findByIdAndUserId(Long id, Long userId) {
        return linkRepository.findById(id)
                .filter(link -> link.getUserId().equals(userId) && !link.isDeleted());
    }

    public Page<Link> findByUserId(Long userId, Pageable pageable) {
        return linkRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
    }

    @Transactional
    public Link updateLink(Long id, Long userId, String title, Boolean active) {
        Link link = linkRepository.findById(id)
                .filter(l -> l.getUserId().equals(userId) && !l.isDeleted())
                .orElseThrow(() -> new LinkNotFoundException("Link not found: " + id));

        if (title != null) {
            link.updateTitle(title);
        }
        if (active != null) {
            link.setActive(active);
        }
        return link;
    }

    @Transactional
    public void deleteLink(Long id, Long userId) {
        Link link = linkRepository.findById(id)
                .filter(l -> l.getUserId().equals(userId) && !l.isDeleted())
                .orElseThrow(() -> new LinkNotFoundException("Link not found: " + id));

        link.softDelete();
    }

    private String generateUniqueShortCode() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String code = shortCodeGenerator.generate();
            if (!linkRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code after " + MAX_RETRIES + " retries");
    }

    public static class LinkNotFoundException extends RuntimeException {
        public LinkNotFoundException(String message) {
            super(message);
        }
    }
}
