package ar.edu.utn.frm.foodstore.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeProfilesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(
                    ApiProfileConfiguration.class,
                    WorkerProfileConfiguration.class,
                    PlanificadorProfileConfiguration.class,
                    MigradorProfileConfiguration.class);

    @ParameterizedTest
    @CsvSource({
            "api, API, servlet",
            "worker, WORKER, none",
            "planificador, PLANIFICADOR, none",
            "migrador, MIGRADOR, none"
    })
    void activatesOnlyTheSelectedRuntimeRole(
            String activeProfile, String expectedRole, String expectedWebApplicationType) {
        contextRunner
                .withPropertyValues("spring.profiles.active=" + activeProfile)
                .run(context -> {
                    assertThat(context).hasSingleBean(RuntimeRole.class);
                    assertThat(context.getBean(RuntimeRole.class).name()).isEqualTo(expectedRole);
                    assertThat(context.getEnvironment()
                            .getProperty("spring.main.web-application-type"))
                            .isEqualTo(expectedWebApplicationType);
                });
    }

    @Test
    void schedulingIsEnabledOnlyForThePlannerProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=planificador")
                .run(context -> assertThat(context)
                        .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));

        for (String profile : new String[]{"api", "worker", "migrador"}) {
            contextRunner
                    .withPropertyValues("spring.profiles.active=" + profile)
                    .run(context -> assertThat(context)
                            .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
        }
    }

    @Test
    void migratorProfileEnablesFlywayAndDisablesHibernateSchemaManagement() {
        contextRunner
                .withPropertyValues("spring.profiles.active=migrador")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.flyway.enabled"))
                            .isEqualTo("true");
                    assertThat(context.getEnvironment()
                            .getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("none");
                });
    }
}
