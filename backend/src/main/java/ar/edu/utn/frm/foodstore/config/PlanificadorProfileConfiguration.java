package ar.edu.utn.frm.foodstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile("planificador")
@EnableScheduling
public class PlanificadorProfileConfiguration {

    @Bean
    RuntimeRole planificadorRuntimeRole() {
        return RuntimeRole.PLANIFICADOR;
    }
}
