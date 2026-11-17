package io.github.kimkimjuhan.ticketrush.concert.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    /** 한 공연의 회차를 시작 시각 순으로. 쿼리 1번으로 가져와 회차마다 따로 조회하는 N+1 문제를 피한다. */
    List<Performance> findByConcertIdOrderByStartsAtAsc(Long concertId);
}
