package io.github.kimkimjuhan.ticketrush.seat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kimkimjuhan.ticketrush.seat.application.ExpiredHoldCleaner;
import io.github.kimkimjuhan.ticketrush.seat.application.SeatLocker;
import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 만료 선점 정리 작업 (F6). */
class ExpiredHoldCleanerTest extends ApiTestSupport {

    @Autowired
    ExpiredHoldCleaner cleaner;

    @Autowired
    SeatLocker seatLocker;

    @Autowired
    PlatformTransactionManager transactionManager;

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

    /**
     * 회귀 테스트 (ADR-0007). 정리 작업이 만료 시각 순서(좌석 2 → 1)로 잠그고 선점 트랜잭션이 ID 순서(1 → 2)로 잠그면
     * 서로 상대가 쥔 행을 기다리는 교착 상태가 된다. PostgreSQL은 그중 하나를 강제로 실패시키므로, 사용자의 선점 요청이
     * 500으로 끝날 수 있다. 정리 작업도 ID 순서로 잠그면 이 순환 대기가 생기지 않는다.
     */
    @Test
    @DisplayName("선점 트랜잭션이 좌석을 잠그고 있는 중에 정리 작업이 돌아도 교착 상태가 생기지 않는다")
    void doesNotDeadlockWithSeatLock() throws Exception {
        long performanceId = insertOpenPerformance();
        long userId = insertUser("holder@example.com");
        long seat1 = insertSeat(performanceId, 1, 1000);
        long seat2 = insertSeat(performanceId, 2, 1000);
        // 좌석 2를 먼저, 더 이른 시각으로 만료시킨다. 정리 쿼리가 인덱스 순서든 물리 순서든 좌석 2를 먼저 만나게 하기 위해서다
        jdbc.update("UPDATE performance_seats SET status='HELD', held_by_user_id=?, hold_expires_at=now() - interval '2 minutes' WHERE id=?", userId, seat2);
        jdbc.update("UPDATE performance_seats SET status='HELD', held_by_user_id=?, hold_expires_at=now() - interval '1 minute' WHERE id=?", userId, seat1);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch seat1Locked = new CountDownLatch(1);
        try {
            // 선점 트랜잭션: 좌석 1 잠금 → 정리 작업이 잠금 대기에 들어갈 때까지 기다림 → 좌석 2 잠금
            Future<?> holder = executor.submit(() -> tx.executeWithoutResult(status -> {
                seatLocker.lockSeats(performanceId, List.of(seat1));
                seat1Locked.countDown();
                awaitCleanerBlockedOnLock();
                seatLocker.lockSeats(performanceId, List.of(seat2));
            }));
            assertThat(seat1Locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Integer> cleaning = executor.submit(cleaner::releaseExpiredHolds);

            holder.get(20, TimeUnit.SECONDS);                       // 교착 상태로 강제 종료되면 여기서 예외
            assertThat(cleaning.get(20, TimeUnit.SECONDS)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
        assertThat(seatStatus(seat1)).isEqualTo("AVAILABLE");
        assertThat(seatStatus(seat2)).isEqualTo("AVAILABLE");
    }

    /** 정리 쿼리가 행 잠금을 기다리는 상태가 될 때까지 기다린다 (pg_stat_activity로 확인). */
    private void awaitCleanerBlockedOnLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock' AND query ILIKE 'UPDATE performance_seats%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("정리 작업이 10초 안에 잠금 대기 상태가 되지 않았습니다.");
    }
}
