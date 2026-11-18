package io.github.kimkimjuhan.ticketrush.reservation.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReserveRequest(
        @NotNull Long performanceId,
        @NotEmpty @Size(max = 10) List<@NotNull Long> seatIds,
        @NotNull @Pattern(regexp = "MOCK", message = "현재는 MOCK 결제만 지원합니다") String paymentMethod
) {
}
