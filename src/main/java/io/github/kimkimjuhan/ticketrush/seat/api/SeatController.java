package io.github.kimkimjuhan.ticketrush.seat.api;

import io.github.kimkimjuhan.ticketrush.seat.application.SeatQueryService;
import io.github.kimkimjuhan.ticketrush.seat.application.dto.SeatMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 좌석 배치도 API (F3). 명세: docs/api/api-v1.md §2 */
@RestController
@RequestMapping("/api/v1/performances/{performanceId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatQueryService seatQueryService;

    @GetMapping
    public SeatMapResponse getSeatMap(@PathVariable Long performanceId) {
        return seatQueryService.getSeatMap(performanceId);
    }
}
