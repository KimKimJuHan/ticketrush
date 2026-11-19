package io.github.kimkimjuhan.ticketrush.common.error;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러 응답을 RFC 9457 Problem Details + {@code code} 필드로 통일한다.
 *
 * <p>세 가지 경로를 처리한다.
 * <ol>
 *   <li>{@link BusinessException}: 예상된 실패. 지정한 코드와 메시지로 응답한다.
 *   <li>Spring MVC 표준 예외(잘못된 경로 변수 타입, 없는 URL 등): 부모 클래스가 만든 응답에 {@code code}만 붙인다.
 *   <li>그 밖의 예외: 버그나 장애. 내부 정보를 숨기고 500으로 응답하며, 원인은 서버 로그에 남긴다.
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CODE = "code";

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        // 비즈니스 실패를 코드별로 센다. NOT_HOLDER 증가 = 선점이 다른 사용자에게 덮어써진 피해 건수 (Lost Update)
        meterRegistry.counter("ticketrush.business.errors", "code", errorCode.code()).increment();
        return ResponseEntity.status(errorCode.status())
                .body(problem(errorCode, ex.getMessage(), request));
    }

    /** 토큰 없음·만료·위조. SecurityConfig의 진입점이 이 핸들러로 넘겨준다. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(errorCode.status())
                .body(problem(errorCode, errorCode.defaultMessage(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(errorCode.status())
                .body(problem(errorCode, errorCode.defaultMessage(), request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외: {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        // 스택 트레이스나 SQL 같은 내부 정보는 응답에 넣지 않는다
        return ResponseEntity.status(errorCode.status())
                .body(problem(errorCode, errorCode.defaultMessage(), request));
    }

    /** Spring MVC 표준 예외 응답에도 {@code code} 필드를 붙인다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            problemDetail.setProperty(CODE, codeFor(statusCode).code());
        }
        return response;
    }

    private static ProblemDetail problem(ErrorCode errorCode, String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(CODE, errorCode.code());
        return problemDetail;
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        if (status.value() == 401) {
            return ErrorCode.UNAUTHORIZED;
        }
        if (status.value() == 403) {
            return ErrorCode.FORBIDDEN;
        }
        if (status.value() == 404) {
            return ErrorCode.NOT_FOUND;
        }
        if (status.is4xxClientError()) {
            return ErrorCode.VALIDATION_FAILED;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
