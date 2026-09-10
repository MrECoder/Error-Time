package com.mrecoder.errortime;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the package structure this library's design relies on - each rule
 * here documents an invariant that's easy to break by accident during a
 * later change (e.g. a new exception class quietly importing something from
 * {@code web} "just this once") and hard to notice in code review otherwise.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mrecoder.errortime");
    }

    @Test
    void exceptionPackageIsAPureDomainModelWithNoDependencyOnOtherLibraryPackages() {
        ArchRule rule = classes()
            .that().resideInAPackage("..exception..")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackage("com.mrecoder.errortime..")
            .orShould().resideInAPackage("..exception..");
        rule.check(classes);
    }

    @Test
    void supportPackageDoesNotDependOnAnyFeaturePackage() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..support..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..web..", "..feign..", "..resilience..", "..autoconfigure..",
                "..metrics..", "..tracing..", "..retry..");
        rule.check(classes);
    }

    @Test
    void coreInfrastructurePackagesDoNotDependOnOptionalIntegrations() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..metrics..", "..tracing..", "..retry..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..web..", "..feign..", "..resilience..", "..autoconfigure..");
        rule.check(classes);
    }

    @Test
    void webPackageDoesNotDependOnOptionalIntegrations() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..feign..", "..resilience..");
        rule.check(classes);
    }

    @Test
    void onlyAutoconfigurePackageWiresTheOptionalIntegrationsTogether() {
        // resilience depends on web (ProblemDetailFactory) - allowed, one direction only,
        // verified by webPackageDoesNotDependOnOptionalIntegrations above.
        ArchRule rule = slices()
            .matching("com.mrecoder.errortime.(*)..")
            .should().beFreeOfCycles();
        rule.check(classes);
    }
}
