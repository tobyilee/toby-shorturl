CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_provider UNIQUE (provider, provider_id)
);

CREATE TABLE links (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(20) NOT NULL,
    original_url TEXT NOT NULL,
    title VARCHAR(500),
    user_id BIGINT NOT NULL REFERENCES users(id),
    click_count BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_links_short_code UNIQUE (short_code)
);

CREATE INDEX idx_links_user_deleted ON links (user_id, deleted_at);

CREATE TABLE click_events (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id),
    clicked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_hash VARCHAR(64),
    user_agent TEXT,
    referer TEXT,
    device_type VARCHAR(20),
    browser VARCHAR(100)
);

CREATE INDEX idx_click_events_link_clicked ON click_events (link_id, clicked_at);
