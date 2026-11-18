package io.github.kimkimjuhan.ticketrush.seat.application.dto;

import java.time.Instant;
import java.util.List;

public record HoldResponse(List<Long> seatIds, Instant expiresAt) {
}
