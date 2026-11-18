package io.github.kimkimjuhan.ticketrush.concert;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 공연 조회 API 통합 테스트 (F2).
 *
 * <p>컨트롤러부터 실제 PostgreSQL까지 전부 거친다. 로그인 없이 호출할 수 있는지(공개 API)도 함께 확인된다.
 * 테스트 데이터는 SQL로 넣는다. 아직 공연 등록 API(F10)가 없기 때문이다.
 */
class ConcertApiTest extends ApiTestSupport {

    @Test
    @DisplayName("목록은 최근 등록순으로 페이지 단위로 조회된다")
    void listsConcertsNewestFirstWithPaging() throws Exception {
        insertConcert("첫 번째 공연");
        insertConcert("두 번째 공연");
        insertConcert("세 번째 공연");

        mvc.perform(get("/api/v1/concerts").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("세 번째 공연"))
                .andExpect(jsonPath("$.content[1].title").value("두 번째 공연"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("상세는 회차를 시작 시각 순으로 포함한다")
    void detailIncludesPerformancesOrderedByStartTime() throws Exception {
        long concertId = insertConcert("겨울 콘서트");
        long later = insertPerformance(concertId, "2026-12-25T10:00:00Z");
        long earlier = insertPerformance(concertId, "2026-12-24T10:00:00Z");

        mvc.perform(get("/api/v1/concerts/{id}", concertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concertId").value(concertId))
                .andExpect(jsonPath("$.title").value("겨울 콘서트"))
                .andExpect(jsonPath("$.performances.length()").value(2))
                .andExpect(jsonPath("$.performances[0].performanceId").value(earlier))
                .andExpect(jsonPath("$.performances[1].performanceId").value(later))
                .andExpect(jsonPath("$.performances[0].startsAt").exists());
    }

    @Test
    @DisplayName("없는 공연을 조회하면 404 NOT_FOUND")
    void returns404ForMissingConcert() throws Exception {
        mvc.perform(get("/api/v1/concerts/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/v1/concerts/999"));
    }

    @Test
    @DisplayName("숫자가 아닌 id는 500이 아니라 400 VALIDATION_FAILED")
    void returns400ForNonNumericId() throws Exception {
        mvc.perform(get("/api/v1/concerts/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("허용하지 않은 필드로 정렬하면 500이 아니라 400 VALIDATION_FAILED")
    void returns400ForUnsupportedSortField() throws Exception {
        mvc.perform(get("/api/v1/concerts").param("sort", "venue,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
