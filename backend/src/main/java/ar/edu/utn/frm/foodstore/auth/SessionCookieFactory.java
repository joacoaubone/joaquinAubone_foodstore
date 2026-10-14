package ar.edu.utn.frm.foodstore.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieFactory {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String ACCESS_PATH = "/api/v1";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private final boolean secure;
    private final String sameSite;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public SessionCookieFactory(
            @Value("${foodstore.auth.cookie.secure:true}") boolean secure,
            @Value("${foodstore.auth.cookie.same-site:Lax}") String sameSite,
            @Value("${foodstore.jwt.access-ttl:PT30M}") Duration accessTokenTtl,
            @Value("${foodstore.jwt.refresh-ttl:P7D}") Duration refreshTokenTtl) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public ResponseCookie accessToken(String token) {
        return cookie(ACCESS_COOKIE, token, ACCESS_PATH, accessTokenTtl);
    }

    public ResponseCookie refreshToken(String token) {
        return cookie(REFRESH_COOKIE, token, REFRESH_PATH, refreshTokenTtl);
    }

    public ResponseCookie clearAccessToken() {
        return cookie(ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO);
    }

    public ResponseCookie clearRefreshToken() {
        return cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO);
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
