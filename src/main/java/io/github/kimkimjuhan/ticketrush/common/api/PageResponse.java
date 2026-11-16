package io.github.kimkimjuhan.ticketrush.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 API의 공통 페이지 응답.
 *
 * <p>Spring Data의 {@code Page}를 그대로 JSON으로 내보내면 내부 구현 필드가 노출되고,
 * 라이브러리 버전이 바뀔 때 응답 형식이 함께 바뀔 수 있다. 그래서 API 계약용 형식을 따로 둔다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
