package io.github.kimkimjuhan.ticketrush.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.kimkimjuhan.ticketrush.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 회원가입·로그인·토큰 인증 통합 테스트 (F1, N5). */
class AuthApiTest extends ApiTestSupport {

    private static final String EMAIL = "fan@example.com";
    private static final String PASSWORD = "correct-horse-1";

    @Test
    @DisplayName("가입하면 201과 사용자 ID를 돌려주고, 비밀번호는 해시로만 저장된다")
    void signupStoresHashedPassword() throws Exception {
        signup(EMAIL, PASSWORD, "팬")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber());

        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, EMAIL);
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
    }

    @Test
    @DisplayName("이미 가입된 이메일은 대소문자가 달라도 409 EMAIL_ALREADY_EXISTS")
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        signup(EMAIL, PASSWORD, "팬").andExpect(status().isCreated());

        signup("FAN@example.com", PASSWORD, "다른 사람")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("짧은 비밀번호나 잘못된 이메일은 400 VALIDATION_FAILED")
    void rejectsInvalidSignupInput() throws Exception {
        signup("not-an-email", "short", "팬")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("로그인하면 Bearer 토큰을 받고, 그 토큰으로 내 정보를 조회할 수 있다")
    void loginIssuesTokenUsableForProtectedApi() throws Exception {
        signup(EMAIL, PASSWORD, "팬").andExpect(status().isCreated());

        String token = loginAndGetToken(EMAIL, PASSWORD);

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value("팬"));
    }

    @Test
    @DisplayName("비밀번호가 틀려도, 없는 이메일이어도 같은 401 INVALID_CREDENTIALS (가입 여부를 알려주지 않음)")
    void loginFailureDoesNotRevealWhetherEmailExists() throws Exception {
        signup(EMAIL, PASSWORD, "팬").andExpect(status().isCreated());

        login(EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("토큰 없이 보호된 API를 호출하면 401 UNAUTHORIZED")
    void protectedApiRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("위조된 토큰은 401 UNAUTHORIZED")
    void rejectsForgedToken() throws Exception {
        signup(EMAIL, PASSWORD, "팬").andExpect(status().isCreated());
        String token = loginAndGetToken(EMAIL, PASSWORD);
        String forged = token.substring(0, token.length() - 4) + "abcd"; // 서명 부분 변조

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions signup(String email, String password, String name) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "name": "%s"}
                        """.formatted(email, password, name)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
