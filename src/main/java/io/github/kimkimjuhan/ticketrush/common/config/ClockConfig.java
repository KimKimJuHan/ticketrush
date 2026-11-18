package io.github.kimkimjuhan.ticketrush.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 {@code Instant.now()} 대신 주입받은 {@link Clock}에서 얻는다.
 *
 * <p>선점 만료(5분)처럼 시간에 따라 결과가 달라지는 로직을, 테스트에서 시계를 고정해 검증할 수 있게 하기 위해서다.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
