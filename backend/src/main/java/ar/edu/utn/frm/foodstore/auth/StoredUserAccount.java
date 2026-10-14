package ar.edu.utn.frm.foodstore.auth;

import java.util.List;

public record StoredUserAccount(long id, String email, String passwordHash, List<String> roles) {

    public StoredUserAccount {
        roles = List.copyOf(roles);
    }

    public UserIdentity identity() {
        return new UserIdentity(id, email, roles);
    }
}
