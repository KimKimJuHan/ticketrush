-- =============================================================================
-- 부하 테스트 시드: "티켓 오픈 직전" 상태를 매번 똑같이 만든다
--
--   공연 1개 · 회차 1개(이미 예매 오픈) · 좌석 100석 · 사용자 1,000명
--   ID를 1부터 다시 매기므로 회차 ID는 항상 1이다 (k6 스크립트의 PERFORMANCE_ID 기본값).
--
-- ⚠️ 모든 테이블을 비운다. 로컬 부하 테스트 스택(deploy/local) 전용이며 다른 DB에서 실행하지 않는다.
--
-- 실행: loadtest/run.ps1 이 자동으로 실행한다. 직접 실행할 때는
--   docker compose -f deploy/local/compose.yaml exec -T postgres \
--     psql -U ticketrush -d ticketrush -v ON_ERROR_STOP=1 -f /loadtest/seed/seed.sql
-- =============================================================================

\set users 1000
\set seats_per_row 10
\set row_count 10

BEGIN;

TRUNCATE payments, reservation_items, reservations, performance_seats, performances, concerts, users
    RESTART IDENTITY CASCADE;

-- 사용자: lt0001@loadtest.local ~ lt1000@loadtest.local
-- 비밀번호는 모두 loadtest-pass-123. 해시를 미리 계산해 DB에 직접 넣는다.
-- BCrypt cost를 운영 기본값(10)이 아닌 4로 낮췄다. 검증 비용이 해시에 기록된 cost를 따르므로
-- 1,000명 로그인이 수 초 안에 끝난다. 로그인 성능은 이 테스트의 측정 대상이 아니다.
INSERT INTO users (email, password_hash, name)
SELECT format('lt%s@loadtest.local', lpad(g::text, 4, '0')),
       '{bcrypt}$2b$04$Fi11KOKeoPFCMgQHcJYTJ.gFnvR5obh3mQU4Q4S2Cnr/Idv5kVTCO',
       format('부하테스트%s', g)
FROM generate_series(1, :users) AS g;

INSERT INTO concerts (title, venue, description)
VALUES ('부하 테스트 콘서트', '테스트 아레나', '좌석 경합 재현용 시드 데이터');

-- 예매는 1시간 전에 열렸고 공연은 30일 뒤
INSERT INTO performances (concert_id, starts_at, booking_opens_at, max_seats_per_user)
VALUES (1, now() + interval '30 days', now() - interval '1 hour', 4);

-- 좌석: A구역 A~J열 × 1~10번 = 100석. 앞쪽 3개 열은 VIP
INSERT INTO performance_seats (performance_id, section, row_label, seat_number, grade, price)
SELECT 1,
       'A',
       chr(64 + r),
       n,
       CASE WHEN r <= 3 THEN 'VIP' ELSE 'R' END,
       CASE WHEN r <= 3 THEN 180000 ELSE 150000 END
FROM generate_series(1, :row_count) AS r,
     generate_series(1, :seats_per_row) AS n
ORDER BY r, n;

COMMIT;

SELECT (SELECT count(*) FROM users)             AS users,
       (SELECT count(*) FROM performance_seats) AS seats,
       (SELECT id FROM performances)            AS performance_id;
