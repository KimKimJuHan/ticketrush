package io.github.kimkimjuhan.ticketrush.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 비밀번호 최대 64자: BCrypt는 72바이트를 넘는 입력을 잘라서 쓰므로, 그보다 짧게 제한한다. */
public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 50) String name
) {
}
