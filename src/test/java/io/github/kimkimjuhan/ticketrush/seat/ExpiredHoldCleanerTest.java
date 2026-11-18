package io.github.kimkimjuhan.ticketrush.seat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kimkimjuhan.ticketrush.seat.application.ExpiredHoldCleaner;
import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 만료 선점 정리 작업 (F6). */
class ExpiredHoldCleanerTest extends ApiTestSupport {

    @Autowired
    ExpiredHoldCleaner cleaner;

    @Test
    @DisplayName("만료된 선점만 AVAILABLE로 되돌리고, 유효한 선점과 판매 좌석은 건드리지 않는다")
    void releasesOnlyExpiredHolds() {
        long performanceId = insertOpenPerformance();
        long userId = insertUser("holder@example.com");
        long expired = insertSeat(performanceId, 1, 1000);
        long active = insertSeat(performanceId, 2, 1000);
        long sold = insertSeat(performanceId, 3, 1000);
        jdbc.update("UPDATE performance_seats SET status='HELD', held_by_user_id=?, hold_expires_at=now() - interval '1 minute' WHERE id=?", userId, expired);
        jdbc.update("UPDATE performance_seats SET status='HELD', held_by_user_id=?, hold_expires_at=now() + interval '5 minutes' WHERE id=?", userId, active);
        jdbc.update("UPDATE performance_seats SET status='SOLD' WHERE id=?", sold);

        int released = cleaner.releaseExpiredHolds();

        assertThat(released).isEqualTo(1);
        assertThat(seatStatus(expired)).isEqualTo("AVAILABLE");
        assertThat(seatStatus(active)).isEqualTo("HELD");
        assertThat(seatStatus(sold)).isEqualTo("SOLD");
    }
}
