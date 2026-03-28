package com.linkly.identity.service;

import com.linkly.identity.domain.User;
import com.linkly.identity.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String providerId = String.valueOf(attributes.get("id"));
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        // GitHub fallback: use "login" attribute if name is null
        if (name == null && "github".equals(provider)) {
            name = (String) attributes.get("login");
        }
        if (name == null) {
            name = "Unknown";
        }

        final String finalName = name;
        userRepository.findByProviderAndProviderId(provider, providerId)
                .ifPresentOrElse(
                        user -> user.updateProfile(email, finalName),
                        () -> userRepository.save(new User(email, finalName, provider, providerId))
                );

        return oAuth2User;
    }
}
