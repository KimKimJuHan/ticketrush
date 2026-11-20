-- =============================================================================
-- 부하 테스트 직후 데이터 정합성 검증
--
-- k6가 보는 것은 "응답"이고, 여기서 보는 것은 "DB에 실제로 남은 상태"다. 둘을 함께 봐야 한다.
--   예) k6에서 선점 성공 응답이 160건이어도, DB에는 좌석당 선점자가 한 명뿐이다 → 누군가는 거짓 성공을 받았다.
-- =============================================================================

\echo
\echo '== 좌석 상태 분포 =='
SELECT status, count(*) AS seats
FROM performance_seats
WHERE performance_id = 1
GROUP BY status
ORDER BY status;

\echo
\echo '== 예매 결과 =='
SELECT (SELECT count(*) FROM reservations WHERE status = 'CONFIRMED')       AS confirmed_reservations,
       (SELECT count(*) FROM reservation_items WHERE cancelled_at IS NULL)  AS sold_items,
       (SELECT count(*) FROM payments WHERE status = 'PAID')                AS paid_payments;

\echo
\echo '== 정합성 검사 (모두 0이어야 PASS) =='
WITH active_items AS (
    SELECT performance_seat_id, count(*) AS n
    FROM reservation_items
    WHERE cancelled_at IS NULL
    GROUP BY performance_seat_id
)
SELECT check_name,
       violations,
       CASE WHEN violations = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       meaning
FROM (
    -- ① 같은 좌석이 두 번 팔렸는가 (ADR-0003 부분 유니크 인덱스가 막아야 한다)
    SELECT 1 AS ord,
           'double_sale' AS check_name,
           (SELECT count(*) FROM active_items WHERE n > 1) AS violations,
           '같은 좌석에 유효한 예매 항목이 2개 이상' AS meaning
    UNION ALL
    -- ② 팔린 좌석인데 좌석 상태가 SOLD가 아닌가
    --    선점 Lost Update가 SOLD를 HELD로 덮어쓰면 여기에 걸린다. 이 좌석은 선점 만료 후 다시 "구매 가능"으로 보인다.
    SELECT 2,
           'sold_but_not_marked',
           (SELECT count(*)
            FROM active_items a
            JOIN performance_seats s ON s.id = a.performance_seat_id
            WHERE s.status <> 'SOLD'),
           '예매된 좌석의 상태가 SOLD가 아님 (상태 덮어쓰기)'
    UNION ALL
    -- ③ SOLD인데 예매 항목이 없는가
    SELECT 3,
           'marked_but_not_sold',
           (SELECT count(*)
            FROM performance_seats s
            WHERE s.status = 'SOLD'
              AND NOT EXISTS (SELECT 1 FROM active_items a WHERE a.performance_seat_id = s.id)),
           'SOLD 좌석에 유효한 예매 항목이 없음'
    UNION ALL
    -- ④ 결제와 예매 건수가 맞는가
    SELECT 4,
           'payment_mismatch',
           (SELECT count(*)
            FROM reservations r
            WHERE r.status = 'CONFIRMED'
              AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.reservation_id = r.id AND p.status = 'PAID')),
           '확정 예매에 결제 기록이 없음'
) checks
ORDER BY ord;
