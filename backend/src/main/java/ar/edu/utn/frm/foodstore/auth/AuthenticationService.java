package ar.edu.utn.frm.foodstore.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$12$" + "a".repeat(53);

    private final UserAccountRepository userAccountRepository;
    private final PasswordHashingService passwordHashingService;
    private final JwtTokenService jwtTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            PasswordHashingService passwordHashingService,
            JwtTokenService jwtTokenService,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHashingService = passwordHashingService;
        this.jwtTokenService = jwtTokenService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    public IssuedSessionTokens login(LoginRequest request) {
        if (loginAttemptLimiter.isLimitExceeded(request.email())) {
            loginAttemptLimiter.recordAttempt(request.email(), false);
            throw new TooManyLoginAttemptsException();
        }

        var account = userAccountRepository.findCredentialsByEmail(request.email());
        String hashToVerify = account.map(StoredUserAccount::passwordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches;
        try {
            passwordMatches = passwordHashingService.matches(request.password(), hashToVerify);
        } catch (PasswordExecutorSaturatedException exception) {
            loginAttemptLimiter.recordAttempt(request.email(), false);
            throw exception;
        }

        loginAttemptLimiter.recordAttempt(request.email(), account.isPresent() && passwordMatches);
        if (account.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return jwtTokenService.issue(account.orElseThrow().identity());
    }

    public JwtTokenService.IssuedAccessToken refresh(String refreshToken) {
        UserIdentity identity = jwtTokenService.parseRefresh(refreshToken);
        return jwtTokenService.issueAccess(identity);
    }
}
