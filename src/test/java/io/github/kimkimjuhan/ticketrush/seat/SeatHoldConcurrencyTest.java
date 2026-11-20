package io.github.kimkimjuhan.ticketrush.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.common.error.ErrorCode;
import io.github.kimkimjuhan.ticketrush.seat.application.SeatHoldService;
import io.github.kimkimjuhan.ticketrush.seat.application.SeatLockMode;
import io.github.kimkimjuhan.ticketrush.seat.application.SeatLocker;
import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 좌석 선점 동시성 테스트 (ADR-0007, 비관적 락).
 *
 * <p>여러 스레드가 시작 신호({@link CountDownLatch})를 기다렸다가 동시에 선점을 호출한다.
 * 각 호출은 서비스의 {@code @Transactional} 경계를 그대로 거치므로, 실제 요청처럼 각자 DB 트랜잭션을 연다.
 *
 * <p>잠금 없음(NONE)에서는 결과가 실행마다 달라 단정할 수 없으므로 검증하지 않는다. 그 동작은 부하 테스트로 측정한다.
 */
class SeatHoldConcurrencyTest extends ApiTestSupport {

    private static final int THREADS = 20;

    @Autowired
    SeatHoldService seatHoldService;

    @Autowired
    SeatLocker seatLocker;

    ExecutorService executor;
    long performanceId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(THREADS);
        performanceId = insertOpenPerformance();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("운영 기본 설정은 비관적 락이고, 실행 중인 잠금 방식을 /actuator/info로 확인할 수 있다")
    void defaultModeIsPessimistic() throws Exception {
        assertThat(seatLocker.mode()).isEqualTo(SeatLockMode.PESSIMISTIC);

        // 부하 테스트가 결과에 실행 설정을 기록할 때 이 값을 읽는다
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketrush['hold-locking']").value("pessimistic"));
    }

    @Test
    @DisplayName("20명이 같은 좌석을 동시에 선점하면 정확히 1명만 성공하고, DB의 선점자는 그 1명이다")
    void onlyOneUserWinsTheSameSeat() throws Exception {
        long seat = insertSeat(performanceId, 1, 110000);
        List<Long> users = insertUsers(THREADS);

        List<Outcome> outcomes = runConcurrently(users.stream()
                .map(userId -> (Callable<Outcome>) () -> attemptHold(userId, List.of(seat)))
                .toList());

        List<Outcome> winners = outcomes.stream().filter(Outcome::success).toList();
        assertThat(winners).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.success())
                .hasSize(THREADS - 1)
                .allSatisfy(o -> assertThat(o.error()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE));

        Long heldBy = jdbc.queryForObject(
                "SELECT held_by_user_id FROM performance_seats WHERE id = ?", Long.class, seat);
        assertThat(heldBy).isEqualTo(winners.getFirst().userId());
    }

    @Test
    @DisplayName("두 좌석을 서로 반대 순서로 동시에 요청해도 교착 상태 없이 한 명만 두 좌석을 모두 얻는다")
    void oppositeOrderRequestsDoNotDeadlock() throws Exception {
        long seatA = insertSeat(performanceId, 1, 110000);
        long seatB = insertSeat(performanceId, 2, 110000);
        List<Long> users = insertUsers(THREADS);

        // 절반은 [A, B], 절반은 [B, A] 순서로 요청한다. 요청 순서대로 잠그면 서로를 기다리는 교착 상태가 생길 수 있다
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            List<Long> order = (i % 2 == 0) ? List.of(seatA, seatB) : List.of(seatB, seatA);
            long userId = users.get(i);
            tasks.add(() -> attemptHold(userId, order));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        List<Outcome> winners = outcomes.stream().filter(Outcome::success).toList();
        assertThat(winners).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.success())
                .allSatisfy(o -> assertThat(o.error()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE));

        // 전부 아니면 전무: 두 좌석의 선점자가 같은 사람이어야 한다
        List<Long> holders = jdbc.queryForList(
                "SELECT held_by_user_id FROM performance_seats WHERE id IN (?, ?)", Long.class, seatA, seatB);
        assertThat(holders).containsOnly(winners.getFirst().userId());
    }

    // ------------------------------------------------------------------ helpers

    private Outcome attemptHold(long userId, List<Long> seatIds) {
        try {
            seatHoldService.hold(userId, performanceId, seatIds);
            return new Outcome(userId, true, null);
        } catch (BusinessException e) {
            return new Outcome(userId, false, e.getErrorCode());
        }
    }

    /** 모든 작업을 준비시킨 뒤 동시에 출발시키고, 결과를 모은다. 예상 밖 예외(교착 상태 등)는 그대로 테스트를 실패시킨다. */
    private List<Outcome> runConcurrently(List<Callable<Outcome>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (Callable<Outcome> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private List<Long> insertUsers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(insertUser("user" + i + "@example.com"));
        }
        return ids;
    }

    private record Outcome(long userId, boolean success, ErrorCode error) {
    }
}
