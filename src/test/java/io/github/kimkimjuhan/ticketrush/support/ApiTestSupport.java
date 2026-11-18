package io.github.kimkimjuhan.ticketrush.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import io.github.kimkimjuhan.ticketrush.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * API 통합 테스트 공통 설정.
 *
 * <ul>
 *   <li>실제 PostgreSQL·Redis(Testcontainers) + 실제 보안 필터를 거친다. 인증 규칙도 함께 검증된다.
 *   <li>테스트마다 데이터를 비워 실행 순서와 무관하게 만든다.
 *   <li>같은 설정을 쓰는 테스트들은 Spring 컨텍스트와 컨테이너를 공유하므로, 테스트가 늘어도 느려지지 않는다.
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbc;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvcAndCleanDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE payments, reservation_items, reservations, performance_seats, "
                + "performances, concerts, users RESTART IDENTITY CASCADE");
    }

    protected long insertConcert(String title) {
        return jdbc.queryForObject(
                "INSERT INTO concerts (title, venue) VALUES (?, '테스트홀') RETURNING id", Long.class, title);
    }

    protected long insertPerformance(long concertId, String startsAtIso) {
        return jdbc.queryForObject("""
                INSERT INTO performances (concert_id, starts_at, booking_opens_at)
                VALUES (?, ?::timestamptz, ?::timestamptz - interval '7 days') RETURNING id
                """, Long.class, concertId, startsAtIso, startsAtIso);
    }

    protected long insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, name) VALUES (?, 'x', '테스트') RETURNING id",
                Long.class, email);
    }
}
