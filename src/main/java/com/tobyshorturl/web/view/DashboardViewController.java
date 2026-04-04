package com.tobyshorturl.web.view;

import com.tobyshorturl.analytics.service.AnalyticsService;
import com.tobyshorturl.identity.domain.User;
import com.tobyshorturl.identity.repository.UserRepository;
import com.tobyshorturl.link.domain.Link;
import com.tobyshorturl.link.service.LinkService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Controller
@RequestMapping("/app")
public class DashboardViewController {

    private final LinkService linkService;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public DashboardViewController(LinkService linkService, UserRepository userRepository,
                                   AnalyticsService analyticsService) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        Long userId = resolveUserId(principal);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Link> links = linkService.findByUserId(userId, pageable);
        model.addAttribute("links", links);
        model.addAttribute("userName", principal.getAttribute("name"));
        return "dashboard";
    }

    @GetMapping("/links/new")
    public String createForm() {
        return "link-create";
    }

    @PostMapping("/links")
    public String createLink(@RequestParam String url,
                             @RequestParam(required = false) String title,
                             @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        linkService.createLink(url, title, userId, null);
        return "redirect:/app/dashboard";
    }

    @GetMapping("/links/{id}")
    public String linkDetail(@PathVariable Long id,
                             @AuthenticationPrincipal OAuth2User principal,
                             Model model) {
        Long userId = resolveUserId(principal);
        Link link = linkService.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        model.addAttribute("link", link);
        model.addAttribute("shortUrl", baseUrl + "/" + link.getShortCode());
        return "link-detail";
    }

    @GetMapping("/links/{id}/stats")
    public String linkStats(@PathVariable Long id,
                            @RequestParam(required = false) String preset,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @AuthenticationPrincipal OAuth2User principal,
                            Model model) {
        Long userId = resolveUserId(principal);
        Link link = linkService.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String activePreset = (preset != null) ? preset : "30d";
        LocalDate today = LocalDate.now();

        if (from == null || to == null) {
            to = today;
            from = switch (activePreset) {
                case "today" -> today;
                case "7d" -> today.minusDays(6);
                case "all" -> link.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
                default -> today.minusDays(29);
            };
        }

        AnalyticsService.StatsV1 stats = analyticsService.getStatsV1(link.getId(), from, to);

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        model.addAttribute("link", link);
        model.addAttribute("shortUrl", baseUrl + "/" + link.getShortCode());
        model.addAttribute("stats", stats);
        model.addAttribute("activePreset", activePreset);
        model.addAttribute("userName", principal.getAttribute("name"));
        return "stats";
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

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return user.getId();
    }
}
