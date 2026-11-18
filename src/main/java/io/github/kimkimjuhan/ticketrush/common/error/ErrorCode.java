package io.github.kimkimjuhan.ticketrush.common.error;

import org.springframework.http.HttpStatus;

/**
 * 클라이언트에 노출하는 에러 코드 목록.
 *
 * <p>클라이언트는 HTTP 상태가 아니라 이 코드로 분기한다. 같은 409라도 "매진"과 "선점 만료"는 처리가 다르기 때문이다.
 * 코드 문자열(enum 이름)은 API 계약이므로 한번 배포하면 바꾸지 않는다. 목록은 docs/api/api-v1.md와 맞춘다.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_HOLDER(HttpStatus.FORBIDDEN, "본인이 선점한 좌석이 아닙니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    BOOKING_NOT_OPEN(HttpStatus.CONFLICT, "예매 가능 기간이 아닙니다."),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT, "이미 다른 사용자가 선점했거나 판매된 좌석입니다."),
    SEAT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "1인당 예매 가능한 좌석 수를 초과했습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "좌석 선점 시간이 만료되었습니다. 다시 선택해 주세요."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "같은 요청이 처리 중입니다. 잠시 후 예매 내역을 확인해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** 응답 JSON의 {@code code} 필드 값. */
    public String code() {
        return name();
    }
}
