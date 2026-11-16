-- =============================================================================
-- V1: 초기 스키마
-- 설계 문서: docs/design/erd.md, 결정 기록: docs/adr/0003-seat-integrity.md
--
-- 규칙
--   * 스키마 변경은 항상 새 버전 파일(V2__...)로 추가한다. 이미 적용된 파일은 수정하지 않는다.
--   * 시간은 모두 timestamptz(UTC 저장)로 둔다.
-- =============================================================================

-- 사용자 ---------------------------------------------------------------------
CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- 공연 -----------------------------------------------------------------------
CREATE TABLE concerts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    venue           VARCHAR(200) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 회차: 한 공연의 특정 날짜·시간 공연 ----------------------------------------
CREATE TABLE performances (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    concert_id          BIGINT      NOT NULL REFERENCES concerts (id),
    starts_at           TIMESTAMPTZ NOT NULL,
    booking_opens_at    TIMESTAMPTZ NOT NULL,
    max_seats_per_user  SMALLINT    NOT NULL DEFAULT 4,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_performances_open_before_start CHECK (booking_opens_at < starts_at),
    CONSTRAINT ck_performances_max_seats CHECK (max_seats_per_user BETWEEN 1 AND 10)
);
CREATE INDEX ix_performances_concert ON performances (concert_id);

-- 회차별 좌석: 판매 단위. 좌석 상태와 선점 정보를 함께 가진다 ----------------
-- status 전이: AVAILABLE -> HELD -> SOLD, HELD -> AVAILABLE(만료·취소), SOLD -> AVAILABLE(예매 취소)
CREATE TABLE performance_seats (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    performance_id      BIGINT       NOT NULL REFERENCES performances (id),
    section             VARCHAR(10)  NOT NULL,
    row_label           VARCHAR(10)  NOT NULL,
    seat_number         INTEGER      NOT NULL,
    grade               VARCHAR(10)  NOT NULL,
    price               INTEGER      NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    held_by_user_id     BIGINT       REFERENCES users (id),
    hold_expires_at     TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_performance_seats_position UNIQUE (performance_id, section, row_label, seat_number),
    CONSTRAINT ck_performance_seats_status CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    CONSTRAINT ck_performance_seats_price CHECK (price >= 0),
    -- HELD 상태일 때만 선점자와 만료 시각이 있어야 한다
    CONSTRAINT ck_performance_seats_hold_fields CHECK (
        (status = 'HELD' AND held_by_user_id IS NOT NULL AND hold_expires_at IS NOT NULL)
        OR (status <> 'HELD' AND held_by_user_id IS NULL AND hold_expires_at IS NULL)
    )
);
-- 좌석 배치도 조회(가장 자주 호출): 회차별 상태 필터
CREATE INDEX ix_performance_seats_performance_status ON performance_seats (performance_id, status);
-- 만료된 선점 일괄 해제 작업용
CREATE INDEX ix_performance_seats_hold_expiry ON performance_seats (hold_expires_at) WHERE status = 'HELD';

-- 예매 -----------------------------------------------------------------------
CREATE TABLE reservations (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id),
    performance_id      BIGINT       NOT NULL REFERENCES performances (id),
    status              VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    total_price         INTEGER      NOT NULL,
    idempotency_key     VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancelled_at        TIMESTAMPTZ,
    CONSTRAINT ck_reservations_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_reservations_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    -- 같은 사용자가 같은 요청을 두 번 보내도 예매는 한 건만 생긴다 (Idempotency-Key 헤더)
    CONSTRAINT uq_reservations_idempotency UNIQUE (user_id, idempotency_key)
);
CREATE INDEX ix_reservations_user ON reservations (user_id, created_at DESC);

-- 예매에 포함된 좌석 -----------------------------------------------------------
CREATE TABLE reservation_items (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id          BIGINT      NOT NULL REFERENCES reservations (id),
    performance_seat_id     BIGINT      NOT NULL REFERENCES performance_seats (id),
    price                   INTEGER     NOT NULL,
    cancelled_at            TIMESTAMPTZ
);
CREATE INDEX ix_reservation_items_reservation ON reservation_items (reservation_id);
-- ★ 중복 판매의 최종 방어선 (ADR-0003)
--   취소되지 않은 예매 항목은 좌석 하나당 최대 한 건만 존재할 수 있다.
--   애플리케이션 로직이 틀려도 DB가 두 번째 판매를 거부한다.
CREATE UNIQUE INDEX ux_reservation_items_active_seat
    ON reservation_items (performance_seat_id)
    WHERE cancelled_at IS NULL;

-- 결제 (모의) ------------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id      BIGINT       NOT NULL REFERENCES reservations (id),
    amount              INTEGER      NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    paid_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PAID', 'REFUNDED'))
);
