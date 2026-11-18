package com.gs5.screening;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * The rules of the architecture, checked on every build instead of promised in a README:
 * the core knows no framework, and Spring appears only where the application is assembled.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.gs5.screening");

    private static final DescribedPredicate<JavaAnnotation<?>> SPRING_ANNOTATION = DescribedPredicate.describe(
            "a Spring annotation", annotation -> annotation.getRawType().getPackageName().startsWith("org.springframework"));

    private static final String COMPOSITION_ROOT = "ScreeningServiceApplication";

    @Test
    void domainDependsOnNothingButItself() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..",
                        "org.springframework..", "tools.jackson..", "org.apache.kafka..", "jakarta..", "org.slf4j..")
                .check(PRODUCTION_CODE);
    }

    @Test
    void applicationDependsOnDomainOnlyAndKnowsNoFramework() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..",
                        "org.springframework..", "tools.jackson..", "org.apache.kafka..", "jakarta..")
                .check(PRODUCTION_CODE);
    }

    @Test
    void springAnnotationsOnlyOnConfigurationClasses() {
        noClasses().that().haveSimpleNameNotEndingWith("Configuration").and().doNotHaveSimpleName(COMPOSITION_ROOT)
                .should().beAnnotatedWith(SPRING_ANNOTATION)
                .because("components are wired explicitly in a *Configuration class, never found by scanning")
                .check(PRODUCTION_CODE);
    }

    @Test
    void noSpringAnnotationsOnMethodsConstructorsOrFieldsOutsideConfiguration() {
        noCodeUnits().that().areDeclaredInClassesThat().haveSimpleNameNotEndingWith("Configuration")
                .should().beAnnotatedWith(SPRING_ANNOTATION)
                .because("no @Transactional, @Scheduled or @KafkaListener proxies - behaviour is explicit code")
                .check(PRODUCTION_CODE);
        noFields().that().areDeclaredInClassesThat().haveSimpleNameNotEndingWith("Configuration")
                .should().beAnnotatedWith(SPRING_ANNOTATION)
                .check(PRODUCTION_CODE);
    }
}
