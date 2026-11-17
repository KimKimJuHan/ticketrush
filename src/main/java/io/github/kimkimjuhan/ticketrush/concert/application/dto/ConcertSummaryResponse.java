package io.github.kimkimjuhan.ticketrush.concert.application.dto;

import io.github.kimkimjuhan.ticketrush.concert.domain.Concert;

/** 공연 목록의 한 줄. 엔티티를 그대로 내보내지 않고 응답 전용 형식을 쓴다(내부 필드 노출·순환 참조 방지). */
public record ConcertSummaryResponse(
        Long concertId,
        String title,
        String venue
) {

    public static ConcertSummaryResponse from(Concert concert) {
        return new ConcertSummaryResponse(concert.getId(), concert.getTitle(), concert.getVenue());
    }
}
