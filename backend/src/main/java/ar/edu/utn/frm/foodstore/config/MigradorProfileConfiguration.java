package ar.edu.utn.frm.foodstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("migrador")
public class MigradorProfileConfiguration {

    @Bean
    RuntimeRole migradorRuntimeRole() {
        return RuntimeRole.MIGRADOR;
    }
}
