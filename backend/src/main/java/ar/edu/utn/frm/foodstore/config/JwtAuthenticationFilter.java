package ar.edu.utn.frm.foodstore.config;

import ar.edu.utn.frm.foodstore.auth.InvalidSessionException;
import ar.edu.utn.frm.foodstore.auth.JwtTokenService;
import ar.edu.utn.frm.foodstore.auth.UserIdentity;
import ar.edu.utn.frm.foodstore.core.error.ApiProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private final JwtTokenService jwtTokenService;
    private final ApiProblemResponseWriter problemResponseWriter;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            ApiProblemResponseWriter problemResponseWriter) {
        this.jwtTokenService = jwtTokenService;
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/v1/roles".equals(path)) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return REGISTER_PATH.equals(path) || LOGIN_PATH.equals(path) || REFRESH_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = accessTokenCookie(request);
        if (token != null) {
            try {
                UserIdentity identity = jwtTokenService.parseAccess(token);
                var authorities = identity.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(identity, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidSessionException exception) {
                SecurityContextHolder.clearContext();
                problemResponseWriter.write(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Sesión inválida",
                        "La cookie access_token está ausente o no es válida.",
                        "SESION_INVALIDA");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String accessTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> ACCESS_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
