package io.github.kimkimjuhan.ticketrush.seat.domain;

/** 좌석 상태. 전이 규칙은 docs/design/seat-state.md. DB CHECK 제약(ck_performance_seats_status)과 값이 같아야 한다. */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    SOLD
}
