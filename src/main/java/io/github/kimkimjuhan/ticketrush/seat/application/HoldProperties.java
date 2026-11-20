package io.github.kimkimjuhan.ticketrush.seat.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 좌석 선점 설정 (application.yml의 ticketrush.hold).
 *
 * @param duration 선점 유지 시간 (F4)
 * @param locking  좌석 상태를 바꿀 때의 잠금 방식 (ADR-0007)
 */
@ConfigurationProperties("ticketrush.hold")
public record HoldProperties(Duration duration, SeatLockMode locking) {

    public HoldProperties {
        if (duration == null) {
            duration = Duration.ofMinutes(5);
        }
        if (locking == null) {
            locking = SeatLockMode.PESSIMISTIC;
        }
    }
}
