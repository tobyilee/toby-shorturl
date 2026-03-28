package com.tobyshorturl.analytics.event;

import com.tobyshorturl.analytics.domain.ClickEvent;
import com.tobyshorturl.analytics.enrichment.UserAgentParser;
import com.tobyshorturl.analytics.privacy.IpHasher;
import com.tobyshorturl.analytics.repository.ClickEventRepository;
import com.tobyshorturl.link.repository.LinkRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClickEventListener {

    private final ClickEventRepository clickEventRepository;
    private final LinkRepository linkRepository;
    private final IpHasher ipHasher;
    private final UserAgentParser userAgentParser;

    public ClickEventListener(ClickEventRepository clickEventRepository,
                              LinkRepository linkRepository,
                              IpHasher ipHasher,
                              UserAgentParser userAgentParser) {
        this.clickEventRepository = clickEventRepository;
        this.linkRepository = linkRepository;
        this.ipHasher = ipHasher;
        this.userAgentParser = userAgentParser;
    }

    @Async("analyticsExecutor")
    @EventListener
    @Transactional
    public void handleClickEvent(ClickEventPayload payload) {
        String ipHash = ipHasher.hash(payload.ipAddress());
        UserAgentParser.ParsedUA parsedUA = userAgentParser.parse(payload.userAgent());

        ClickEvent clickEvent = new ClickEvent(
                payload.linkId(),
                payload.clickedAt(),
                ipHash,
                payload.userAgent(),
                payload.referer(),
                parsedUA.deviceType(),
                parsedUA.browser()
        );

        clickEventRepository.save(clickEvent);
        linkRepository.incrementClickCount(payload.linkId());
    }
}
