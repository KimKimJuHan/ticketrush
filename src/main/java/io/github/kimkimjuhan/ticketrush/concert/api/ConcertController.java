package io.github.kimkimjuhan.ticketrush.concert.api;

import io.github.kimkimjuhan.ticketrush.common.api.PageResponse;
import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.common.error.ErrorCode;
import io.github.kimkimjuhan.ticketrush.concert.application.ConcertQueryService;
import io.github.kimkimjuhan.ticketrush.concert.application.dto.ConcertDetailResponse;
import io.github.kimkimjuhan.ticketrush.concert.application.dto.ConcertSummaryResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공연 조회 API (F2). 명세: docs/api/api-v1.md §2 */
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    /**
     * 정렬을 허용하는 필드.
     *
     * <p>클라이언트가 보낸 sort 값을 그대로 쓰면 (1) 없는 필드는 500 에러가 되고
     * (2) 인덱스 없는 컬럼 정렬로 DB에 부하를 줄 수 있다. 그래서 허용 목록으로 제한한다.
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "title");

    private final ConcertQueryService concertQueryService;

    @GetMapping
    public PageResponse<ConcertSummaryResponse> getConcerts(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        validateSort(pageable.getSort());
        return concertQueryService.getConcerts(pageable);
    }

    @GetMapping("/{concertId}")
    public ConcertDetailResponse getConcert(@PathVariable Long concertId) {
        return concertQueryService.getConcert(concertId);
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "정렬할 수 없는 필드입니다: " + order.getProperty() + " (허용: " + SORTABLE_FIELDS + ")");
            }
        }
    }
}
