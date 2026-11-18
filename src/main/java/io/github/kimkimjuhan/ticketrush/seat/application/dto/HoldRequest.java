package io.github.kimkimjuhan.ticketrush.seat.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 상한 10은 DB의 max_seats_per_user 최대값. 실제 한도는 회차 설정으로 다시 검사한다. */
public record HoldRequest(
        @NotEmpty @Size(max = 10) List<@NotNull Long> seatIds
) {
}
