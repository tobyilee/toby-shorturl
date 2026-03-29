# PRD: Short URL Copy to Clipboard

## Introduction

The link detail page (`/app/links/{id}`) currently shows only the short code (e.g., `abc123`), not the full short URL. Users need to see the complete clickable short URL and be able to copy it to clipboard with a single click. The base URL is derived from the current request's host.

## Goals

- Display the full short URL (e.g., `http://localhost:8080/abc123`) instead of just the short code
- Provide a copy-to-clipboard button with visual feedback (icon change + "Copied!" text)
- Use the current request's host to construct the short URL dynamically

## User Stories

### US-001: Show full short URL with copy button on link detail page
**Description:** As a user, I want to see the full short URL on the link detail page and copy it to clipboard with one click, so I can easily share it.

**Acceptance Criteria:**
- [ ] The "Short Code" field is replaced with a "Short URL" field showing the full URL (e.g., `http://localhost:8080/abc123`)
- [ ] The short URL is constructed using the current request's scheme and host (not hardcoded)
- [ ] A copy icon (SVG clipboard icon) is displayed next to the short URL
- [ ] Clicking the icon copies the full short URL to the clipboard using the Clipboard API
- [ ] After copying, the icon changes to a checkmark and "Copied!" text appears briefly (2 seconds)
- [ ] After 2 seconds, the icon reverts to the original clipboard icon and the "Copied!" text disappears
- [ ] The short URL is displayed as a clickable link that opens in a new tab
- [ ] Typecheck passes
- [ ] Verify in browser using dev-browser skill

## Functional Requirements

- FR-1: The controller must pass a `shortUrl` variable to the template, constructed from the current request's scheme, host, port, and the link's short code
- FR-2: The template must display the full short URL as a clickable `<a>` tag with `target="_blank"`
- FR-3: An inline SVG copy icon must be placed next to the short URL
- FR-4: JavaScript `navigator.clipboard.writeText()` must be used to copy the URL on icon click
- FR-5: On successful copy, the icon must change to a checkmark SVG and show "Copied!" text for 2 seconds
- FR-6: The "Short Code" label and field must be replaced (not added alongside)

## Non-Goals

- No toast/snackbar notification system
- No keyboard shortcut for copying
- No copy button on the dashboard list view (only link detail page)

## Technical Considerations

- Use `HttpServletRequest` or `ServletUriComponentsBuilder` in the controller to build the base URL
- Use inline JavaScript in the Thymeleaf template (no external JS files needed for this small feature)
- Use inline SVG icons to avoid external icon library dependencies
- `navigator.clipboard.writeText()` requires HTTPS in production but works on `localhost` over HTTP

## Success Metrics

- User can copy the short URL in one click
- Visual feedback confirms the copy action

## Open Questions

None — scope is clear.
