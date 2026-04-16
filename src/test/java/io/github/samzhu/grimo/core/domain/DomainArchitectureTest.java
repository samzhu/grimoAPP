package io.github.samzhu.grimo.core.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-3 guard: asserts that the {@code core.domain} package never picks
 * up a Spring or {@code jakarta.annotation} dependency.
 *
 * <p>The domain layer is deliberately Spring-free (development-standards
 * §2). Sneaking an {@code @Component} or {@code @Autowired} into a
 * record would betray the layering and make the domain types unusable
 * from non-Spring code paths (native hardening, future stand-alone
 * CLI bootstrapping). The rule below is the mechanical gate.
 *
 * <p>ArchUnit {@code 1.4.1} is transitively provided by
 * {@code spring-modulith-starter-test} — no new dependency.
 */
class DomainArchitectureTest {

    @Test
    @DisplayName("AC-3 core.domain has no Spring or jakarta.annotation imports")
    void noSpringDependenciesInDomain() {
        // Given — import only production classes under core.domain
        var classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.samzhu.grimo.core.domain");

        // When — build the rule
        ArchRule rule = noClasses()
            .that().resideInAPackage("io.github.samzhu.grimo.core.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.annotation..");

        // Then — rule must hold
        rule.check(classes);
    }
}
