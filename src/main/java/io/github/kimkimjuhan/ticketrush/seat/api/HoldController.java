package io.github.kimkimjuhan.ticketrush.seat.api;

import io.github.kimkimjuhan.ticketrush.common.security.CurrentUser;
import io.github.kimkimjuhan.ticketrush.seat.application.SeatHoldService;
import io.github.kimkimjuhan.ticketrush.seat.application.dto.HoldRequest;
import io.github.kimkimjuhan.ticketrush.seat.application.dto.HoldResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 좌석 선점 API (F4). 명세: docs/api/api-v1.md §3 */
@RestController
@RequestMapping("/api/v1/performances/{performanceId}/holds")
@RequiredArgsConstructor
public class HoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse hold(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable Long performanceId,
                             @Valid @RequestBody HoldRequest request) {
        return seatHoldService.hold(CurrentUser.id(jwt), performanceId, request.seatIds());
    }

    @DeleteMapping("/{seatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long performanceId,
                        @PathVariable Long seatId) {
        seatHoldService.release(CurrentUser.id(jwt), performanceId, seatId);
    }
}
