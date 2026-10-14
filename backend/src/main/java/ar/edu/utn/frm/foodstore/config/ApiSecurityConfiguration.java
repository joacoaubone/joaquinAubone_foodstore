package ar.edu.utn.frm.foodstore.config;

import ar.edu.utn.frm.foodstore.core.error.ApiProblemResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class ApiSecurityConfiguration {

    @Bean
    UserDetailsService passwordAuthenticationIsDisabled() {
        return username -> {
            throw new UsernameNotFoundException("La autenticación se realiza exclusivamente con JWT");
        };
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiProblemResponseWriter problemResponseWriter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problemResponseWriter.write(
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "Sesión inválida",
                                "La cookie access_token está ausente o no es válida.",
                                "SESION_INVALIDA"))
                        .accessDeniedHandler((request, response, exception) -> problemResponseWriter.write(
                                response,
                                HttpStatus.FORBIDDEN,
                                "Rol insuficiente",
                                "El usuario autenticado no tiene permiso para esta operación.",
                                "ROL_INSUFICIENTE")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
