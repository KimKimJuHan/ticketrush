package io.github.kimkimjuhan.ticketrush.concert.application;

import io.github.kimkimjuhan.ticketrush.common.api.PageResponse;
import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.concert.application.dto.ConcertDetailResponse;
import io.github.kimkimjuhan.ticketrush.concert.application.dto.ConcertSummaryResponse;
import io.github.kimkimjuhan.ticketrush.concert.domain.Concert;
import io.github.kimkimjuhan.ticketrush.concert.domain.ConcertRepository;
import io.github.kimkimjuhan.ticketrush.concert.domain.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공연 조회 전용 서비스.
 *
 * <p>readOnly 트랜잭션: 변경 감지를 끄고, 나중에 읽기 전용 DB 복제본으로 보낼 수 있는 표시가 된다.
 * 엔티티는 트랜잭션 안에서 응답 DTO로 바꿔서 내보낸다. open-in-view를 껐기 때문에(application.yml)
 * 트랜잭션 밖에서 LAZY 필드를 건드리면 예외가 나므로, 변환을 여기서 끝낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertQueryService {

    private final ConcertRepository concertRepository;
    private final PerformanceRepository performanceRepository;

    public PageResponse<ConcertSummaryResponse> getConcerts(Pageable pageable) {
        return PageResponse.from(concertRepository.findAll(pageable).map(ConcertSummaryResponse::from));
    }

    public ConcertDetailResponse getConcert(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> BusinessException.notFound("공연", concertId));
        return ConcertDetailResponse.of(concert, performanceRepository.findByConcertIdOrderByStartsAtAsc(concertId));
    }
}
