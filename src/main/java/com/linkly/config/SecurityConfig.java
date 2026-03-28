package com.linkly.config;

import com.linkly.identity.service.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/app/login", "/error", "/static/**").permitAll()
                .requestMatchers("/api/**", "/app/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/app/login")
                .defaultSuccessUrl("/app/dashboard", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
            )
            .logout(logout -> logout
                .logoutUrl("/app/logout")
                .logoutSuccessUrl("/app/login?logout")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );

        return http.build();
    }
}
