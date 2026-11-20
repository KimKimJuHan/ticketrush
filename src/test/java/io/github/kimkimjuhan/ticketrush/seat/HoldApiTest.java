package io.github.kimkimjuhan.ticketrush.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 좌석 선점 API 통합 테스트 (F4).
 *
 * <p>순차 요청 기준의 규칙을 검증한다. 동시 요청에서 한 좌석에 한 명만 선점되는지는
 * {@link SeatHoldConcurrencyTest}가 검증한다 (ADR-0007).
 */
class HoldApiTest extends ApiTestSupport {

    long performanceId;
    long seat1;
    long seat2;
    String alice;
    String bob;

    @BeforeEach
    void setUp() throws Exception {
        performanceId = insertOpenPerformance();
        seat1 = insertSeat(performanceId, 1, 110000);
        seat2 = insertSeat(performanceId, 2, 110000);
        alice = signupAndLogin("alice@example.com");
        bob = signupAndLogin("bob@example.com");
    }

    @Test
    @DisplayName("선점하면 201과 만료 시각을 돌려주고 좌석은 HELD가 된다")
    void holdsSeats() throws Exception {
        hold(alice, seat1, seat2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatIds.length()").value(2))
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThat(seatStatus(seat1)).isEqualTo("HELD");
        assertThat(seatStatus(seat2)).isEqualTo("HELD");
    }

    @Test
    @DisplayName("다른 사람이 선점한 좌석이 하나라도 있으면 409이고, 나머지 좌석도 선점되지 않는다 (전부 아니면 전무)")
    void isAllOrNothing() throws Exception {
        hold(bob, seat2).andExpect(status().isCreated());

        hold(alice, seat1, seat2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

        assertThat(seatStatus(seat1)).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("선점 시간이 지난 좌석은 다른 사람이 선점할 수 있다")
    void expiredHoldCanBeTakenByOthers() throws Exception {
        hold(bob, seat1).andExpect(status().isCreated());
        jdbc.update("UPDATE performance_seats SET hold_expires_at = now() - interval '1 second' WHERE id = ?", seat1);

        hold(alice, seat1).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("1인 매수 한도(4석)를 넘으면 409 SEAT_LIMIT_EXCEEDED")
    void enforcesSeatLimit() throws Exception {
        long s3 = insertSeat(performanceId, 3, 110000);
        long s4 = insertSeat(performanceId, 4, 110000);
        long s5 = insertSeat(performanceId, 5, 110000);
        hold(alice, seat1, seat2, s3).andExpect(status().isCreated());

        hold(alice, s4, s5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("예매 오픈 전 회차는 409 BOOKING_NOT_OPEN")
    void rejectsBeforeBookingOpens() throws Exception {
        jdbc.update("UPDATE performances SET booking_opens_at = now() + interval '1 day' WHERE id = ?", performanceId);

        hold(alice, seat1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_OPEN"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void requiresLogin() throws Exception {
        mvc.perform(post("/api/v1/performances/{id}/holds", performanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIds\": [%d]}".formatted(seat1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("본인 선점은 해제할 수 있고, 남의 선점은 403 NOT_HOLDER")
    void releasesOnlyOwnHold() throws Exception {
        hold(alice, seat1).andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/performances/{p}/holds/{s}", performanceId, seat1)
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOLDER"));

        mvc.perform(delete("/api/v1/performances/{p}/holds/{s}", performanceId, seat1)
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());
        assertThat(seatStatus(seat1)).isEqualTo("AVAILABLE");
    }

    private ResultActions hold(String token, long... seatIds) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(seatIds).mapToObj(String::valueOf).toList());
        return mvc.perform(post("/api/v1/performances/{id}/holds", performanceId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatIds\": [" + ids + "]}"));
    }
}
