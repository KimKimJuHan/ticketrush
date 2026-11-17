package io.github.kimkimjuhan.ticketrush.concert.application.dto;

import io.github.kimkimjuhan.ticketrush.concert.domain.Concert;
import io.github.kimkimjuhan.ticketrush.concert.domain.Performance;
import java.time.Instant;
import java.util.List;

/** 공연 상세 + 회차 목록. */
public record ConcertDetailResponse(
        Long concertId,
        String title,
        String venue,
        String description,
        List<PerformanceItem> performances
) {

    public static ConcertDetailResponse of(Concert concert, List<Performance> performances) {
        return new ConcertDetailResponse(
                concert.getId(),
                concert.getTitle(),
                concert.getVenue(),
                concert.getDescription(),
                performances.stream().map(PerformanceItem::from).toList());
    }

    public record PerformanceItem(
            Long performanceId,
            Instant startsAt,
            Instant bookingOpensAt
    ) {

        static PerformanceItem from(Performance performance) {
            return new PerformanceItem(performance.getId(), performance.getStartsAt(), performance.getBookingOpensAt());
        }
    }
}
