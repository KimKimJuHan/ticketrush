package io.github.kimkimjuhan.ticketrush.auth.application;

import io.github.kimkimjuhan.ticketrush.auth.application.dto.LoginRequest;
import io.github.kimkimjuhan.ticketrush.auth.application.dto.LoginResponse;
import io.github.kimkimjuhan.ticketrush.auth.application.dto.SignupRequest;
import io.github.kimkimjuhan.ticketrush.auth.application.dto.SignupResponse;
import io.github.kimkimjuhan.ticketrush.common.error.BusinessException;
import io.github.kimkimjuhan.ticketrush.common.error.ErrorCode;
import io.github.kimkimjuhan.ticketrush.common.security.JwtTokenProvider;
import io.github.kimkimjuhan.ticketrush.common.security.JwtTokenProvider.IssuedToken;
import io.github.kimkimjuhan.ticketrush.user.domain.User;
import io.github.kimkimjuhan.ticketrush.user.domain.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * 없는 이메일로 로그인할 때도 비밀번호 검사와 비슷한 시간이 걸리게 하기 위한 더미 해시.
     * 응답 시간 차이로 "이 이메일은 가입되어 있다"를 알아내는 공격(사용자 열거)을 어렵게 한다.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        try {
            User user = userRepository.saveAndFlush(
                    User.register(email, passwordEncoder.encode(request.password()), request.name().strip()));
            return new SignupResponse(user.getId());
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 같은 이메일로 동시에 가입한 경우. DB 유니크 제약이 최종 방어선이다
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 이메일이 틀렸는지 비밀번호가 틀렸는지 구분해서 알려주지 않는다
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        IssuedToken token = tokenProvider.issue(user.getId(), user.getRole().name());
        return LoginResponse.bearer(token.value(), token.expiresInSeconds());
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
