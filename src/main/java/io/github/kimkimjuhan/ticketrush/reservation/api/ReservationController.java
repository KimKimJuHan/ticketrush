package io.github.kimkimjuhan.ticketrush.reservation.api;

import io.github.kimkimjuhan.ticketrush.common.security.CurrentUser;
import io.github.kimkimjuhan.ticketrush.reservation.application.ReservationService;
import io.github.kimkimjuhan.ticketrush.reservation.application.dto.ReservationResponse;
import io.github.kimkimjuhan.ticketrush.reservation.application.dto.ReserveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 예매 확정 API (F5). 명세: docs/api/api-v1.md §4 */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@AuthenticationPrincipal Jwt jwt,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                       @Valid @RequestBody ReserveRequest request) {
        return reservationService.reserve(CurrentUser.id(jwt), idempotencyKey, request);
    }
}
