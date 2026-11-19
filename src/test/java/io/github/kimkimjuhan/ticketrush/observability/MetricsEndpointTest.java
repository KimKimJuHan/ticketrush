package io.github.kimkimjuhan.ticketrush.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;

/**
 * Prometheus가 수집하는 지표 엔드포인트. 대시보드가 의존하는 지표 이름이 실제로 나오는지 확인한다.
 *
 * <p>Spring Boot는 테스트에서 지표 내보내기(Prometheus 등)를 기본으로 끈다. 테스트가 실제 모니터링 시스템에
 * 데이터를 보내지 않게 하기 위해서다. 이 테스트는 엔드포인트 자체를 검증하므로 {@link AutoConfigureMetrics}로 켠다.
 * (설정이 달라 Spring 컨텍스트를 따로 띄우므로 다른 테스트보다 몇 초 느리다)
 */
@AutoConfigureMetrics
class MetricsEndpointTest extends ApiTestSupport {

    @Test
    @DisplayName("/actuator/prometheus는 인증 없이 열리고, 대시보드가 쓰는 비즈니스 지표와 커넥션 풀 지표를 내보낸다")
    void exposesMetricsForDashboard() throws Exception {
        mvc.perform(get("/api/v1/concerts")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/concerts/{id}", 999)).andExpect(status().isNotFound()); // 비즈니스 실패 1건

        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // http_server_requests(응답시간 히스토그램)는 여기서 검사하지 않는다. 이 지표는 서블릿 필터가 기록하는데,
        // 테스트의 MockMvc에는 보안 필터만 붙어 있어 기록되지 않는다. 실제 서버에서는 자동 등록되며,
        // 로컬 관측성 스택(deploy/local)의 Grafana에서 확인한다.
        assertThat(body)
                .contains("application=\"ticketrush\"")                 // 공통 태그
                .contains("ticketrush_business_errors_total{")          // 코드별 비즈니스 실패
                .contains("code=\"NOT_FOUND\"")
                .contains("hikaricp_connections_active");               // 커넥션 풀
    }
}
