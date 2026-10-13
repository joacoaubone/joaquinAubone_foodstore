package ar.edu.utn.frm.foodstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("worker")
public class WorkerProfileConfiguration {

    @Bean
    RuntimeRole workerRuntimeRole() {
        return RuntimeRole.WORKER;
    }
}
