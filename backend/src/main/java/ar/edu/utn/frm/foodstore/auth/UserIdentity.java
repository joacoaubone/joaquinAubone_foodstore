package ar.edu.utn.frm.foodstore.auth;

import java.util.List;

public record UserIdentity(long id, String email, List<String> roles) {

    public UserIdentity {
        roles = List.copyOf(roles);
    }
}
