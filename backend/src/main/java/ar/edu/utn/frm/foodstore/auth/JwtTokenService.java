package ar.edu.utn.frm.foodstore.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final Set<String> SUPPORTED_ROLES = Set.of("ADMIN", "STOCK", "PEDIDOS", "CLIENTE");

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    @Autowired
    public JwtTokenService(
            @Value("${foodstore.jwt.secret}") String base64Secret,
            @Value("${foodstore.jwt.access-ttl:PT30M}") Duration accessTokenTtl,
            @Value("${foodstore.jwt.refresh-ttl:P7D}") Duration refreshTokenTtl) {
        this(base64Secret, accessTokenTtl, refreshTokenTtl, Clock.systemUTC());
    }

    JwtTokenService(
            String base64Secret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    public IssuedSessionTokens issue(UserIdentity identity) {
        var issuedAt = clock.instant();
        var accessExpiresAt = issuedAt.plus(accessTokenTtl);
        var refreshExpiresAt = issuedAt.plus(refreshTokenTtl);
        String accessToken = issueToken(identity, "access", issuedAt, accessExpiresAt);
        String refreshToken = issueToken(identity, "refresh", issuedAt, refreshExpiresAt);
        SessionInfo sessionInfo = new SessionInfo(OffsetDateTime.ofInstant(accessExpiresAt, ZoneOffset.UTC));
        return new IssuedSessionTokens(accessToken, refreshToken, sessionInfo);
    }

    public IssuedAccessToken issueAccess(UserIdentity identity) {
        var issuedAt = clock.instant();
        var accessExpiresAt = issuedAt.plus(accessTokenTtl);
        String accessToken = issueToken(identity, "access", issuedAt, accessExpiresAt);
        SessionInfo sessionInfo = new SessionInfo(OffsetDateTime.ofInstant(accessExpiresAt, ZoneOffset.UTC));
        return new IssuedAccessToken(accessToken, sessionInfo);
    }

    public UserIdentity parseAccess(String token) {
        return parseToken(token, "access");
    }

    public UserIdentity parseRefresh(String token) {
        return parseToken(token, "refresh");
    }

    private UserIdentity parseToken(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new InvalidSessionException();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get("tipo", String.class))) {
                throw new InvalidSessionException();
            }

            long userId = Long.parseLong(claims.getSubject());
            String email = claims.get("email", String.class);
            Object roleClaim = claims.get("roles");
            if (userId <= 0 || email == null || email.isBlank() || !(roleClaim instanceof List<?> roleValues)) {
                throw new InvalidSessionException();
            }

            List<String> roles = new ArrayList<>();
            for (Object roleValue : roleValues) {
                if (!(roleValue instanceof String role) || !SUPPORTED_ROLES.contains(role)) {
                    throw new InvalidSessionException();
                }
                roles.add(role);
            }
            if (roles.isEmpty()) {
                throw new InvalidSessionException();
            }

            return new UserIdentity(userId, email, roles);
        } catch (InvalidSessionException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidSessionException(exception);
        }
    }

    private String issueToken(
            UserIdentity identity, String tokenType, java.time.Instant issuedAt, java.time.Instant expiresAt) {
        return Jwts.builder()
                .subject(Long.toString(identity.id()))
                .claim("email", identity.email())
                .claim("roles", identity.roles())
                .claim("tipo", tokenType)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public record IssuedAccessToken(String token, SessionInfo sessionInfo) {
    }
}
