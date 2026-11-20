package io.github.kimkimjuhan.ticketrush.seat.application;

import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeat;
import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeatRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태를 바꿀 좌석을 읽는 단일 진입점. 선점·선점 해제·예매 확정이 모두 이 클래스를 거친다.
 *
 * <p>잠금 방식을 한 곳에 모아 두면, 어느 경로 하나가 잠금 없이 좌석을 읽어 다시 Lost Update가 생기는 실수를 막을 수 있고,
 * 설정값 하나로 실험 조건(잠금 없음 / 비관적 락)을 바꿀 수 있다.
 *
 * <p>행 잠금은 트랜잭션이 끝날 때 풀리므로, 반드시 호출하는 쪽의 트랜잭션 안에서 불러야 한다 ({@code MANDATORY}).
 */
@Slf4j
@Component
public class SeatLocker {

    private final PerformanceSeatRepository seatRepository;
    private final SeatLockMode mode;
    private final Timer lockWaitTimer;

    public SeatLocker(PerformanceSeatRepository seatRepository, HoldProperties holdProperties, MeterRegistry meterRegistry) {
        this.seatRepository = seatRepository;
        this.mode = holdProperties.locking();
        // 좌석 행을 읽는 데 걸린 시간. 비관적 락에서는 대부분 다른 트랜잭션의 커밋을 기다린 시간이다.
        // DB 커넥션 대기(hikaricp_connections_acquire)와 분리해서 병목 위치를 판단하기 위해 따로 잰다.
        this.lockWaitTimer = Timer.builder("ticketrush.seat.lock.wait")
                .description("상태를 바꿀 좌석 행을 읽는(잠그는) 데 걸린 시간")
                .tag("mode", mode.name().toLowerCase())
                .register(meterRegistry);
        if (mode == SeatLockMode.NONE) {
            log.warn("좌석 잠금 방식: NONE. 동시 선점이 덮어써질 수 있습니다. 부하 테스트 기준선 재현용 설정입니다.");
        } else {
            log.info("좌석 잠금 방식: {}", mode);
        }
    }

    /**
     * 이 회차의 좌석들을 상태 변경용으로 읽는다. 결과는 좌석 ID 오름차순이다.
     *
     * <p>비관적 락에서 ID 순서로 잠그는 이유: 사용자 A가 [1, 2], B가 [2, 1] 순서로 잠그면 서로 상대가 쥔 행을 기다리는
     * 교착 상태(deadlock)가 생긴다. 모든 트랜잭션이 같은 순서로 잠그면 이 순환 대기가 생기지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<PerformanceSeat> lockSeats(Long performanceId, Collection<Long> seatIds) {
        return lockWaitTimer.record(() -> switch (mode) {
            case NONE -> seatRepository.findByPerformanceIdAndIdInOrderById(performanceId, seatIds);
            case PESSIMISTIC -> seatRepository.findForUpdate(performanceId, seatIds);
        });
    }

    public SeatLockMode mode() {
        return mode;
    }
}
