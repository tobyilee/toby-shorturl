package com.tobyshorturl.link.service;

import com.tobyshorturl.link.domain.Link;
import com.tobyshorturl.link.repository.LinkRepository;
import com.tobyshorturl.link.shortcode.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    LinkRepository linkRepository;

    @Mock
    ShortCodeGenerator shortCodeGenerator;

    @InjectMocks
    LinkService linkService;

    @Test
    void createLinkGeneratesShortCodeAndSaves() {
        when(shortCodeGenerator.generate()).thenReturn("abc123");
        when(linkRepository.existsByShortCode("abc123")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        Link link = linkService.createLink("https://example.com", "Example", 1L, null);

        assertEquals("abc123", link.getShortCode());
        assertEquals("https://example.com", link.getOriginalUrl());
        assertEquals("Example", link.getTitle());
        assertEquals(1L, link.getUserId());
        verify(linkRepository).save(any(Link.class));
    }

    @Test
    void createLinkRetriesOnCollision() {
        when(shortCodeGenerator.generate()).thenReturn("collision", "unique1");
        when(linkRepository.existsByShortCode("collision")).thenReturn(true);
        when(linkRepository.existsByShortCode("unique1")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        Link link = linkService.createLink("https://example.com", "Example", 1L, null);

        assertEquals("unique1", link.getShortCode());
        verify(shortCodeGenerator, times(2)).generate();
    }

    @Test
    void findByShortCodeReturnsAccessibleLink() {
        Link link = new Link("abc123", "https://example.com", "Example", 1L, null);
        when(linkRepository.findByShortCode("abc123")).thenReturn(Optional.of(link));

        Optional<Link> result = linkService.findAccessibleByShortCode("abc123");

        assertTrue(result.isPresent());
        assertEquals("abc123", result.get().getShortCode());
    }

    @Test
    void findByShortCodeReturnsEmptyForDeletedLink() {
        Link link = new Link("abc123", "https://example.com", "Example", 1L, null);
        link.softDelete();
        when(linkRepository.findByShortCode("abc123")).thenReturn(Optional.of(link));

        Optional<Link> result = linkService.findAccessibleByShortCode("abc123");

        assertTrue(result.isEmpty());
    }
}
