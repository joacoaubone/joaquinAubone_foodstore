package ar.edu.utn.frm.foodstore.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final JwtTokenService tokenService = new JwtTokenService(
            TEST_SECRET,
            Duration.ofMinutes(30),
            Duration.ofDays(7),
            Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void eachTokenTypeIsAcceptedOnlyByItsIntendedValidator() {
        UserIdentity identity = new UserIdentity(42, "user@example.test", List.of("CLIENTE"));
        IssuedSessionTokens tokens = tokenService.issue(identity);

        assertThat(tokenService.parseAccess(tokens.accessToken())).isEqualTo(identity);
        assertThat(tokenService.parseRefresh(tokens.refreshToken())).isEqualTo(identity);
        assertThatThrownBy(() -> tokenService.parseAccess(tokens.refreshToken()))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> tokenService.parseRefresh(tokens.accessToken()))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void sessionExpiryRepresentsTheThirtyMinuteAccessToken() {
        IssuedSessionTokens tokens = tokenService.issue(
                new UserIdentity(42, "user@example.test", List.of("CLIENTE")));

        assertThat(tokens.sessionInfo().expiresAt())
                .isEqualTo(Instant.parse("2026-10-14T12:30:00Z").atOffset(ZoneOffset.UTC));
    }

    @Test
    void rejectsMalformedAndEmptyTokensAsInvalidSession() {
        assertThatThrownBy(() -> tokenService.parseAccess("not-a-jwt"))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> tokenService.parseRefresh(" "))
                .isInstanceOf(InvalidSessionException.class);
    }
}
