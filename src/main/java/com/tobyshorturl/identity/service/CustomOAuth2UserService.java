package com.tobyshorturl.identity.service;

import com.tobyshorturl.identity.domain.User;
import com.tobyshorturl.identity.repository.UserRepository;
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
        String rawName = (String) attributes.get("name");
        if (rawName == null && "github".equals(provider)) {
            rawName = (String) attributes.get("login");
        }
        final String name = rawName != null ? rawName : "Unknown";
        userRepository.findByProviderAndProviderId(provider, providerId)
                .ifPresentOrElse(
                        user -> user.updateProfile(email, name),
                        () -> userRepository.save(new User(email, name, provider, providerId))
                );

        return oAuth2User;
    }
}
