package io.github.kimkimjuhan.ticketrush.common.error;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. {@link GlobalExceptionHandler}가 RFC 9457 응답으로 바꾼다.
 *
 * <p>예상된 실패(없는 공연, 매진 등)에만 쓴다. 버그나 장애는 이 예외로 감싸지 않고 그대로 던져 500으로 기록되게 한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BusinessException notFound(String resource, Object id) {
        return new BusinessException(ErrorCode.NOT_FOUND, resource + "을(를) 찾을 수 없습니다. id=" + id);
    }
}
