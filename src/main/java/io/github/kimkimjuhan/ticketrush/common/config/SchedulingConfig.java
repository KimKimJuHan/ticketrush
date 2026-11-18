package io.github.kimkimjuhan.ticketrush.common.config;

import io.github.kimkimjuhan.ticketrush.seat.application.HoldProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업(@Scheduled) 활성화.
 *
 * <p>서버가 여러 대면 각 서버에서 따로 실행된다. 지금 작업(만료 선점 해제)은 멱등이라 문제없지만,
 * 한 번만 실행돼야 하는 작업이 생기면 분산 락(ShedLock 등)을 검토한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(HoldProperties.class)
public class SchedulingConfig {
}
