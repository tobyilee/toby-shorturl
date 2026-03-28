package com.tobyshorturl.analytics.api;

import com.tobyshorturl.analytics.service.AnalyticsService;
import com.tobyshorturl.identity.domain.User;
import com.tobyshorturl.identity.repository.UserRepository;
import com.tobyshorturl.link.domain.Link;
import com.tobyshorturl.link.service.LinkService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/links/{linkId}/stats")
public class AnalyticsApiController {

    private final AnalyticsService analyticsService;
    private final LinkService linkService;
    private final UserRepository userRepository;

    public AnalyticsApiController(AnalyticsService analyticsService, LinkService linkService,
                                  UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.linkService = linkService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Object getStats(@PathVariable Long linkId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestHeader(value = "X-API-Version", defaultValue = "1") String apiVersion,
                           @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        verifyOwnership(linkId, userId);

        if ("2".equals(apiVersion)) {
            return analyticsService.getStatsV2(linkId, from, to);
        }
        return analyticsService.getStatsV1(linkId, from, to);
    }

    private void verifyOwnership(Long linkId, Long userId) {
        linkService.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));
    }

    private Long resolveUserId(OAuth2User principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String providerId = principal.getAttribute("sub");
        String provider = "google";

        if (providerId == null) {
            providerId = String.valueOf(principal.getAttribute("id"));
            provider = "github";
        }

        String finalProviderId = providerId;
        String finalProvider = provider;
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + finalProvider + "/" + finalProviderId));
        return user.getId();
    }
}
