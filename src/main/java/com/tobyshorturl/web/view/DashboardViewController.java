package com.tobyshorturl.web.view;

import com.tobyshorturl.identity.domain.User;
import com.tobyshorturl.identity.repository.UserRepository;
import com.tobyshorturl.link.domain.Link;
import com.tobyshorturl.link.service.LinkService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/app")
public class DashboardViewController {

    private final LinkService linkService;
    private final UserRepository userRepository;

    public DashboardViewController(LinkService linkService, UserRepository userRepository) {
        this.linkService = linkService;
        this.userRepository = userRepository;
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
        model.addAttribute("link", link);
        return "link-detail";
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
