package io.github.kimkimjuhan.ticketrush.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 예매 확정 API 통합 테스트 (F5). */
class ReservationApiTest extends ApiTestSupport {

    long performanceId;
    long seat1;
    long seat2;
    String alice;
    String bob;

    @BeforeEach
    void setUp() throws Exception {
        performanceId = insertOpenPerformance();
        seat1 = insertSeat(performanceId, 1, 110000);
        seat2 = insertSeat(performanceId, 2, 165000);
        alice = signupAndLogin("alice@example.com");
        bob = signupAndLogin("bob@example.com");
    }

    @Test
    @DisplayName("선점한 좌석을 예매하면 201 CONFIRMED, 좌석은 SOLD, 결제 1건이 기록된다")
    void confirmsHeldSeats() throws Exception {
        hold(alice, seat1, seat2);

        reserve(alice, UUID.randomUUID().toString(), seat1, seat2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalPrice").value(275000))
                .andExpect(jsonPath("$.seatIds.length()").value(2));

        assertThat(seatStatus(seat1)).isEqualTo("SOLD");
        assertThat(seatStatus(seat2)).isEqualTo("SOLD");
        assertThat(count("payments")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다시 요청하면 새 예매·결제 없이 처음 결과를 돌려준다")
    void isIdempotent() throws Exception {
        hold(alice, seat1);
        String key = UUID.randomUUID().toString();

        String first = reserve(alice, key, seat1).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = reserve(alice, key, seat1).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(count("reservations")).isEqualTo(1);
        assertThat(count("payments")).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 사람이 선점한 좌석은 403 NOT_HOLDER")
    void rejectsSeatHeldByOthers() throws Exception {
        hold(bob, seat1);

        reserve(alice, UUID.randomUUID().toString(), seat1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOLDER"));
    }

    @Test
    @DisplayName("선점 시간이 지나면 409 HOLD_EXPIRED")
    void rejectsExpiredHold() throws Exception {
        hold(alice, seat1);
        jdbc.update("UPDATE performance_seats SET hold_expires_at = now() - interval '1 second' WHERE id = ?", seat1);

        reserve(alice, UUID.randomUUID().toString(), seat1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400")
    void requiresIdempotencyKey() throws Exception {
        hold(alice, seat1);

        mvc.perform(post("/api/v1/reservations")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(seat1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void hold(String token, long... seatIds) throws Exception {
        mvc.perform(post("/api/v1/performances/{id}/holds", performanceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": " + ids(seatIds) + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions reserve(String token, String idempotencyKey, long... seatIds) throws Exception {
        return mvc.perform(post("/api/v1/reservations")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(seatIds)));
    }

    private String body(long... seatIds) {
        return """
                {"performanceId": %d, "seatIds": %s, "paymentMethod": "MOCK"}
                """.formatted(performanceId, ids(seatIds));
    }

    private static String ids(long... seatIds) {
        return java.util.Arrays.toString(seatIds);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
