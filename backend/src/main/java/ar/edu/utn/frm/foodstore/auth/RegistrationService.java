package ar.edu.utn.frm.foodstore.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final String CUSTOMER_ROLE = "CLIENTE";

    private final UserAccountRepository userAccountRepository;
    private final PasswordHashingService passwordHashingService;

    public RegistrationService(
            UserAccountRepository userAccountRepository,
            PasswordHashingService passwordHashingService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHashingService = passwordHashingService;
    }

    @Transactional
    public UserIdentity registerCustomer(RegisterRequest request) {
        return createUser(request.email(), request.password(), CUSTOMER_ROLE);
    }

    @Transactional
    public UserIdentity provision(AdminUserCreateRequest request) {
        return createUser(request.email(), request.password(), request.rol_codigo());
    }

    private UserIdentity createUser(String email, String password, String roleCode) {
        if (userAccountRepository.emailExists(email)) {
            throw new EmailAlreadyExistsException();
        }

        String passwordHash = passwordHashingService.hash(password);
        try {
            return userAccountRepository.create(email, passwordHash, roleCode);
        } catch (DuplicateKeyException exception) {
            // The unique database constraint resolves races after the initial existence check.
            throw new EmailAlreadyExistsException();
        }
    }
}
