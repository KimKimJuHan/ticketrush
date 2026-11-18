package io.github.kimkimjuhan.ticketrush.seat.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceSeatRepository extends JpaRepository<PerformanceSeat, Long> {

    /** 한 회차의 좌석 전체를 쿼리 1번으로. 인덱스 ix_performance_seats_performance_status를 탄다. */
    List<PerformanceSeat> findByPerformanceIdOrderBySectionAscRowLabelAscSeatNumberAsc(Long performanceId);
}
