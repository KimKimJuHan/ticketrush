package io.github.kimkimjuhan.ticketrush.seat.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 좌석 선점 설정 (application.yml의 ticketrush.hold). */
@ConfigurationProperties("ticketrush.hold")
public record HoldProperties(Duration duration) {

    public HoldProperties {
        if (duration == null) {
            duration = Duration.ofMinutes(5);
        }
    }
}
