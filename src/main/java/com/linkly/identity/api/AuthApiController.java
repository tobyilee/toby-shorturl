package com.linkly.identity.api;

import com.linkly.identity.domain.User;
import com.linkly.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserRepository userRepository;

    public AuthApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserResponse(Long id, String email, String name, String provider) {}

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal OAuth2User principal) {
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getProvider());
    }
}
