package io.github.kimkimjuhan.ticketrush.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prometheus가 수집하는 지표 엔드포인트. 대시보드가 의존하는 지표 이름이 실제로 나오는지 확인한다. */
class MetricsEndpointTest extends ApiTestSupport {

    @Test
    @DisplayName("/actuator/prometheus는 인증 없이 열리고, 응답시간 히스토그램과 비즈니스 지표를 내보낸다")
    void exposesMetricsForDashboard() throws Exception {
        mvc.perform(get("/api/v1/concerts")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/concerts/{id}", 999)).andExpect(status().isNotFound()); // 비즈니스 실패 1건

        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("http_server_requests_seconds_bucket")       // p95 계산용 히스토그램
                .contains("application=\"ticketrush\"")                 // 공통 태그
                .contains("ticketrush_business_errors_total{")          // 코드별 비즈니스 실패
                .contains("code=\"NOT_FOUND\"")
                .contains("hikaricp_connections_active");               // 커넥션 풀
    }
}
