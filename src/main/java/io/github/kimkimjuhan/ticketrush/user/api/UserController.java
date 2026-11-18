package io.github.kimkimjuhan.ticketrush.user.api;

import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.common.security.CurrentUser;
import io.github.kimkimjuhan.ticketrush.user.domain.User;
import io.github.kimkimjuhan.ticketrush.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 사용자 본인 정보. 토큰이 제대로 동작하는지 확인하는 가장 단순한 보호 API이기도 하다. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = CurrentUser.id(jwt);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("사용자", userId)); // 토큰 발급 후 탈퇴한 경우
        return new MeResponse(user.getId(), user.getEmail(), user.getName());
    }

    public record MeResponse(Long userId, String email, String name) {
    }
}
