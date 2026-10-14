package ar.edu.utn.frm.foodstore.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$12$" + "a".repeat(53);

    private final UserAccountRepository userAccountRepository;
    private final PasswordHashingService passwordHashingService;
    private final JwtTokenService jwtTokenService;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            PasswordHashingService passwordHashingService,
            JwtTokenService jwtTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHashingService = passwordHashingService;
        this.jwtTokenService = jwtTokenService;
    }

    public IssuedSessionTokens login(LoginRequest request) {
        var account = userAccountRepository.findCredentialsByEmail(request.email());
        String hashToVerify = account.map(StoredUserAccount::passwordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordHashingService.matches(request.password(), hashToVerify);

        if (account.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return jwtTokenService.issue(account.orElseThrow().identity());
    }
}
