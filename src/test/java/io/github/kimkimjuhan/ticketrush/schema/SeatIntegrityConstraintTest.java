package io.github.kimkimjuhan.ticketrush.schema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kimkimjuhan.ticketrush.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ADR-0003 검증: 애플리케이션 로직과 무관하게 DB가 좌석 중복 판매를 거부하는가.
 *
 * <p>실제 PostgreSQL(Testcontainers)에 Flyway 마이그레이션을 적용한 상태에서 SQL로 직접 확인한다.
 * 테스트마다 데이터를 비우므로 실행 순서와 무관하다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SeatIntegrityConstraintTest {

    @Autowired
    JdbcTemplate jdbc;

    long buyerA;
    long buyerB;
    long performanceId;
    long seatId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE payments, reservation_items, reservations, performance_seats, "
                + "performances, concerts, users RESTART IDENTITY CASCADE");

        buyerA = insertUser("a@example.com");
        buyerB = insertUser("b@example.com");
        long concertId = jdbc.queryForObject(
                "INSERT INTO concerts (title, venue) VALUES ('테스트 공연', '테스트홀') RETURNING id", Long.class);
        performanceId = jdbc.queryForObject("""
                INSERT INTO performances (concert_id, starts_at, booking_opens_at)
                VALUES (?, now() + interval '30 days', now() - interval '1 day') RETURNING id
                """, Long.class, concertId);
        seatId = jdbc.queryForObject("""
                INSERT INTO performance_seats (performance_id, section, row_label, seat_number, grade, price)
                VALUES (?, 'A', '1', 1, 'VIP', 165000) RETURNING id
                """, Long.class, performanceId);
    }

    @Test
    @DisplayName("같은 좌석을 두 번째로 판매하면 DB가 거부한다")
    void rejectsSecondActiveSaleOfSameSeat() {
        sell(buyerA, seatId);

        assertThatThrownBy(() -> sell(buyerB, seatId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_reservation_items_active_seat");
    }

    @Test
    @DisplayName("취소된 판매가 있는 좌석은 다시 판매할 수 있다")
    void allowsResaleAfterCancellation() {
        long itemId = sell(buyerA, seatId);
        jdbc.update("UPDATE reservation_items SET cancelled_at = now() WHERE id = ?", itemId);

        assertThatCode(() -> sell(buyerB, seatId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HELD 상태인데 선점자가 없으면 DB가 거부한다")
    void rejectsHeldSeatWithoutHolder() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE performance_seats SET status = 'HELD' WHERE id = ?", seatId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_performance_seats_hold_fields");
    }

    private long insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, name) VALUES (?, 'x', '테스트') RETURNING id",
                Long.class, email);
    }

    /** 예매 1건과 좌석 1개짜리 예매 항목을 만들고 항목 id를 돌려준다. */
    private long sell(long userId, long seat) {
        long reservationId = jdbc.queryForObject("""
                INSERT INTO reservations (user_id, performance_id, total_price)
                VALUES (?, ?, 165000) RETURNING id
                """, Long.class, userId, performanceId);
        return jdbc.queryForObject("""
                INSERT INTO reservation_items (reservation_id, performance_seat_id, price)
                VALUES (?, ?, 165000) RETURNING id
                """, Long.class, reservationId, seat);
    }
}
