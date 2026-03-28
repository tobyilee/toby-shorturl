package com.linkly.link.api;

import com.linkly.identity.domain.User;
import com.linkly.identity.repository.UserRepository;
import com.linkly.link.repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LinkApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LinkRepository linkRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("test@example.com", "Test User", "google", "123456"));
    }

    @Test
    void createLinkReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/links")
                        .with(oauth2Login().attributes(attrs -> {
                            attrs.put("sub", "123456");
                            attrs.put("name", "Test User");
                        }))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "title": "Example"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode", not(emptyString())))
                .andExpect(jsonPath("$.originalUrl", is("https://example.com")));
    }

    @Test
    void createLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "title": "Example"}
                                """))
                .andExpect(status().is3xxRedirection());
    }
}
