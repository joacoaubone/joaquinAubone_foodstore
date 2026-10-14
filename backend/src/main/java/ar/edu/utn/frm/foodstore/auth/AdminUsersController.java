package ar.edu.utn.frm.foodstore.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
public class AdminUsersController {

    private final RegistrationService registrationService;

    public AdminUsersController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserIdentity> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        UserIdentity identity = registrationService.provision(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(identity);
    }
}
