package com.tobyshorturl.link.api;

import com.tobyshorturl.identity.domain.User;
import com.tobyshorturl.identity.repository.UserRepository;
import com.tobyshorturl.link.domain.Link;
import com.tobyshorturl.link.service.LinkService;
import com.tobyshorturl.qr.service.QrCodeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/links")
public class LinkApiController {

    private final LinkService linkService;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;

    public LinkApiController(LinkService linkService, UserRepository userRepository, QrCodeService qrCodeService) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.qrCodeService = qrCodeService;
    }

    public record CreateLinkRequest(@NotBlank @URL String url, String title, Instant expiresAt) {}
    public record UpdateLinkRequest(String title, Boolean active) {}
    public record LinkResponse(Long id, String shortCode, String originalUrl, String title,
                               long clickCount, boolean active, Instant expiresAt, Instant createdAt) {
        public static LinkResponse from(Link link) {
            return new LinkResponse(link.getId(), link.getShortCode(), link.getOriginalUrl(),
                    link.getTitle(), link.getClickCount(), link.isActive(),
                    link.getExpiresAt(), link.getCreatedAt());
        }
    }

    @PostMapping
    public ResponseEntity<LinkResponse> createLink(@Valid @RequestBody CreateLinkRequest request,
                                                    @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        Link link = linkService.createLink(request.url(), request.title(), userId, request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkResponse.from(link));
    }

    @GetMapping
    public Page<LinkResponse> listLinks(@AuthenticationPrincipal OAuth2User principal, Pageable pageable) {
        Long userId = resolveUserId(principal);
        return linkService.findByUserId(userId, pageable).map(LinkResponse::from);
    }

    @GetMapping("/{id}")
    public LinkResponse getLink(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        Link link = linkService.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return LinkResponse.from(link);
    }

    @PatchMapping("/{id}")
    public LinkResponse updateLink(@PathVariable Long id,
                                   @RequestBody UpdateLinkRequest request,
                                   @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        Link link = linkService.updateLink(id, userId, request.title(), request.active());
        return LinkResponse.from(link);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        linkService.deleteLink(id, userId);
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<byte[]> getQrCode(@PathVariable Long id,
                                            @RequestParam(defaultValue = "200") int size,
                                            @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        Link link = linkService.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        byte[] png = qrCodeService.generateQrCode("https://linkly.com/" + link.getShortCode(), size);
        String etag = "\"" + link.getShortCode() + "-" + size + "\"";

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .header(HttpHeaders.ETAG, etag)
                .body(png);
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
