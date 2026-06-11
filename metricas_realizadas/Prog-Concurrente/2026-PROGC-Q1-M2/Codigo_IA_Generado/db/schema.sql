-- =============================================================================
-- Sistema de Venta de Entradas para Recitales
-- Schema PostgreSQL
-- =============================================================================

-- Users
CREATE TABLE IF NOT EXISTS users (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(50)  UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(100),
    full_name     VARCHAR(100),
    created_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- Concerts (populated from JSON files at startup)
CREATE TABLE IF NOT EXISTS concerts (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    artist      VARCHAR(200) NOT NULL,
    event_date  TIMESTAMPTZ  NOT NULL,
    venue       VARCHAR(200) NOT NULL,
    description TEXT,
    json_file   VARCHAR(255) UNIQUE,   -- prevents duplicate loads
    is_active   BOOLEAN      DEFAULT TRUE,
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

-- Seats
-- status transitions: available → reserved → sold
--                     reserved → available  (timeout or cancel)
CREATE TABLE IF NOT EXISTS seats (
    id                     SERIAL PRIMARY KEY,
    concert_id             INTEGER      NOT NULL REFERENCES concerts(id) ON DELETE CASCADE,
    section                VARCHAR(20)  NOT NULL CHECK (section IN ('campo', 'platea', 'platea_vip')),
    row_label              VARCHAR(10)  NOT NULL,
    seat_number            VARCHAR(10)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'available'
                               CHECK (status IN ('available', 'reserved', 'sold')),
    price                  NUMERIC(10,2) NOT NULL,
    -- reservation fields (NULL when available or sold)
    reserved_by            INTEGER      REFERENCES users(id),
    reserved_at            TIMESTAMPTZ,
    reservation_expires_at TIMESTAMPTZ,
    -- sale fields
    sold_at                TIMESTAMPTZ,
    sold_to                INTEGER      REFERENCES users(id),
    UNIQUE (concert_id, section, row_label, seat_number)
);

-- Indexes for high-concurrency read/write patterns
CREATE INDEX IF NOT EXISTS idx_seats_concert_status
    ON seats (concert_id, status);

CREATE INDEX IF NOT EXISTS idx_seats_reservation_expires
    ON seats (reservation_expires_at)
    WHERE status = 'reserved';

-- Race condition audit log
CREATE TABLE IF NOT EXISTS race_condition_log (
    id             SERIAL PRIMARY KEY,
    seat_id        INTEGER     REFERENCES seats(id),
    loser_user_id  INTEGER     REFERENCES users(id),
    loser_username VARCHAR(50),
    thread_name    VARCHAR(100),
    occurred_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rclog_occurred_at ON race_condition_log (occurred_at DESC);
