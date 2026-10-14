package ar.edu.utn.frm.foodstore.auth;

public record IssuedSessionTokens(String accessToken, String refreshToken, SessionInfo sessionInfo) {
}
