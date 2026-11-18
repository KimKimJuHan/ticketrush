package io.github.kimkimjuhan.ticketrush.seat.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceSeatRepository extends JpaRepository<PerformanceSeat, Long> {

    /** 한 회차의 좌석 전체를 쿼리 1번으로. 인덱스 ix_performance_seats_performance_status를 탄다. */
    List<PerformanceSeat> findByPerformanceIdOrderBySectionAscRowLabelAscSeatNumberAsc(Long performanceId);

    /** 다른 회차의 좌석 ID를 섞어 보내는 요청을 막기 위해 회차 조건을 함께 건다. */
    List<PerformanceSeat> findByPerformanceIdAndIdIn(Long performanceId, Collection<Long> seatIds);

    /** 이 사용자가 이 회차에서 현재 선점 중인 좌석 수 (1인 매수 제한 계산용). */
    @Query("""
            select count(s) from PerformanceSeat s
            where s.performanceId = :performanceId
              and s.status = io.github.kimkimjuhan.ticketrush.seat.domain.SeatStatus.HELD
              and s.heldByUserId = :userId
              and s.holdExpiresAt > :now
            """)
    long countActiveHolds(@Param("performanceId") Long performanceId,
                          @Param("userId") Long userId,
                          @Param("now") Instant now);

    /**
     * 만료된 선점을 한 번에 해제한다 (F6).
     *
     * <p>엔티티를 하나씩 읽어 바꾸지 않고 UPDATE 한 문장으로 처리한다. 서버가 여러 대여도 같은 행을 같은 값으로
     * 바꾸는 멱등 연산이라 중복 실행돼도 결과가 같다. 부분 인덱스 ix_performance_seats_hold_expiry를 탄다.
     */
    @Modifying
    @Query(value = """
            UPDATE performance_seats
               SET status = 'AVAILABLE', held_by_user_id = NULL, hold_expires_at = NULL, updated_at = now()
             WHERE status = 'HELD' AND hold_expires_at <= :now
            """, nativeQuery = true)
    int releaseExpiredHolds(@Param("now") Instant now);
}
