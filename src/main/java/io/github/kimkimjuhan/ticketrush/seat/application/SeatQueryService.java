package io.github.kimkimjuhan.ticketrush.seat.application;

import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.concert.domain.PerformanceRepository;
import io.github.kimkimjuhan.ticketrush.seat.application.dto.SeatMapResponse;
import io.github.kimkimjuhan.ticketrush.seat.domain.PerformanceSeatRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 좌석 배치도 조회 (F3). 부하 테스트에서 가장 많이 호출될 API라 쿼리 수를 2번으로 고정한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatQueryService {

    private final PerformanceRepository performanceRepository;
    private final PerformanceSeatRepository seatRepository;
    private final Clock clock;

    public SeatMapResponse getSeatMap(Long performanceId) {
        if (!performanceRepository.existsById(performanceId)) {
            throw BusinessException.notFound("회차", performanceId);
        }
        return SeatMapResponse.of(
                performanceId,
                seatRepository.findByPerformanceIdOrderBySectionAscRowLabelAscSeatNumberAsc(performanceId),
                clock.instant());
    }
}
