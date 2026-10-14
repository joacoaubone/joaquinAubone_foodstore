package ar.edu.utn.frm.foodstore.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final SessionCookieFactory cookieFactory;

    public AuthenticationController(
            AuthenticationService authenticationService,
            SessionCookieFactory cookieFactory) {
        this.authenticationService = authenticationService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/login")
    public ResponseEntity<SessionInfo> login(@Valid @RequestBody LoginRequest request) {
        IssuedSessionTokens session = authenticationService.login(request);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieFactory.accessToken(session.accessToken()).toString());
        headers.add(HttpHeaders.SET_COOKIE, cookieFactory.refreshToken(session.refreshToken()).toString());
        return new ResponseEntity<>(session.sessionInfo(), headers, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessToken().toString());
        headers.add(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshToken().toString());
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionInfo> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        JwtTokenService.IssuedAccessToken accessToken = authenticationService.refresh(refreshToken);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieFactory.accessToken(accessToken.token()).toString());
        return new ResponseEntity<>(accessToken.sessionInfo(), headers, HttpStatus.OK);
    }

    @GetMapping("/me")
    public UserIdentity currentIdentity(@AuthenticationPrincipal UserIdentity identity) {
        return identity;
    }
}
