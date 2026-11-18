package io.github.kimkimjuhan.ticketrush.seat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 좌석 배치도 API 통합 테스트 (F3). */
class SeatApiTest extends ApiTestSupport {

    long performanceId;
    long holderId;

    @BeforeEach
    void setUp() {
        performanceId = insertPerformance(insertConcert("겨울 콘서트"), "2026-12-24T10:00:00Z");
        holderId = insertUser("holder@example.com");
    }

    @Test
    @DisplayName("좌석 상태는 AVAILABLE/UNAVAILABLE 두 가지로만 노출되고, 만료된 선점은 AVAILABLE이다")
    void exposesOnlyAvailabilityAndTreatsExpiredHoldAsAvailable() throws Exception {
        insertSeat(1, "AVAILABLE", null, null);
        insertSeat(2, "HELD", holderId, "now() + interval '5 minutes'"); // 선점 중
        insertSeat(3, "HELD", holderId, "now() - interval '1 minute'");  // 선점 만료, 정리 전
        insertSeat(4, "SOLD", null, null);

        mvc.perform(get("/api/v1/performances/{id}/seats", performanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performanceId").value(performanceId))
                .andExpect(jsonPath("$.seats.length()").value(4))
                .andExpect(jsonPath("$.seats[0].number").value(1))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.seats[2].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[3].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.seats[0].grade").value("VIP"))
                .andExpect(jsonPath("$.seats[0].price").value(165000));
    }

    @Test
    @DisplayName("로그인 없이 조회할 수 있다")
    void isPublic() throws Exception {
        mvc.perform(get("/api/v1/performances/{id}/seats", performanceId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 회차는 404 NOT_FOUND")
    void returns404ForMissingPerformance() throws Exception {
        mvc.perform(get("/api/v1/performances/{id}/seats", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void insertSeat(int number, String status, Long heldBy, String holdExpiresSql) {
        jdbc.update("""
                INSERT INTO performance_seats
                    (performance_id, section, row_label, seat_number, grade, price, status, held_by_user_id, hold_expires_at)
                VALUES (?, 'A', '1', ?, 'VIP', 165000, ?, ?, %s)
                """.formatted(holdExpiresSql == null ? "NULL" : holdExpiresSql),
                performanceId, number, status, heldBy);
    }
}
