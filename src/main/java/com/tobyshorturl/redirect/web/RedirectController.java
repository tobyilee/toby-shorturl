package com.tobyshorturl.redirect.web;

import com.tobyshorturl.analytics.event.ClickEventPayload;
import com.tobyshorturl.link.domain.Link;
import com.tobyshorturl.link.repository.LinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;

@Controller
public class RedirectController {

    private final LinkRepository linkRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RedirectController(LinkRepository linkRepository, ApplicationEventPublisher eventPublisher) {
        this.linkRepository = linkRepository;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]{4,20}}")
    public String redirect(@PathVariable String shortCode, HttpServletRequest request) {
        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));

        if (link.isDeleted() || link.isExpired()) {
            throw new ErrorResponseException(HttpStatus.GONE);
        }

        if (!link.isActive()) {
            throw new ErrorResponseException(HttpStatus.NOT_FOUND);
        }

        eventPublisher.publishEvent(new ClickEventPayload(
                link.getId(),
                Instant.now(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("Referer")
        ));

        return "redirect:" + link.getOriginalUrl();
    }
}
