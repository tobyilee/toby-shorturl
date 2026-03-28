package com.tobyshorturl.redirect.web;

import com.tobyshorturl.identity.domain.User;
import com.tobyshorturl.identity.repository.UserRepository;
import com.tobyshorturl.link.domain.Link;
import com.tobyshorturl.link.repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RedirectControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LinkRepository linkRepository;

    @Autowired
    UserRepository userRepository;

    private Link testLink;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(new User("test@example.com", "Test User", "google", "123"));
        testLink = linkRepository.save(new Link("abc123", "https://example.com", "Example", user.getId(), null));
    }

    @Test
    void redirectsToOriginalUrl() throws Exception {
        mockMvc.perform(get("/abc123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://example.com"));
    }

    @Test
    void returns404ForUnknownCode() throws Exception {
        mockMvc.perform(get("/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns410ForDeletedLink() throws Exception {
        testLink.softDelete();
        linkRepository.save(testLink);

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isGone());
    }
}
