package io.github.kimkimjuhan.ticketrush.seat.application;

import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeatRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 선점을 주기적으로 AVAILABLE로 되돌린다 (F6).
 *
 * <p>조회·선점 로직은 이미 만료 시각으로 판단하므로, 이 작업이 늦게 돌아도 정합성에는 문제가 없다.
 * DB에 남은 HELD 상태를 실제와 맞춰 두는 정리 작업이다.
 */
@Component
@RequiredArgsConstructor
public class ExpiredHoldCleaner {

    private static final Logger log = LoggerFactory.getLogger(ExpiredHoldCleaner.class);

    private final PerformanceSeatRepository seatRepository;
    private final Clock clock;

    @Scheduled(initialDelayString = "${ticketrush.hold.cleanup-interval-ms:30000}",
            fixedDelayString = "${ticketrush.hold.cleanup-interval-ms:30000}")
    @Transactional
    public int releaseExpiredHolds() {
        int released = seatRepository.releaseExpiredHolds(clock.instant());
        if (released > 0) {
            log.info("만료된 좌석 선점 {}건 해제", released);
        }
        return released;
    }
}
