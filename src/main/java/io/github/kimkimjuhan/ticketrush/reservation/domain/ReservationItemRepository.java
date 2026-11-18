package io.github.kimkimjuhan.ticketrush.reservation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationItemRepository extends JpaRepository<ReservationItem, Long> {

    /** 이 사용자가 이 회차에서 이미 구매한(취소되지 않은) 좌석 수. 1인 매수 제한 계산용. */
    @Query("""
            select count(i) from ReservationItem i
            where i.reservation.userId = :userId
              and i.reservation.performanceId = :performanceId
              and i.cancelledAt is null
            """)
    long countActiveByUserAndPerformance(@Param("userId") Long userId, @Param("performanceId") Long performanceId);
}
