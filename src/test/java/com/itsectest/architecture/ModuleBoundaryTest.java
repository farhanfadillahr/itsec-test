package com.itsectest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("module boundaries")
class ModuleBoundaryTest {

    private static final String ROOT = "com.itsectest";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionCode() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    private static ArchRule internalsAreModulePrivate(String module) {
        return classes()
                .that().resideInAPackage(ROOT + "." + module + ".internal..")
                .should().onlyBeAccessed().byAnyPackage(ROOT + "." + module + "..")
                .because(module + " exposes its api and domain packages; internal is its own business");
    }

    @Test
    void aModuleNeverReachesIntoAnotherModulesInternals() {
        internalsAreModulePrivate("user").check(production);
        internalsAreModulePrivate("auth").check(production);
        internalsAreModulePrivate("article").check(production);
        internalsAreModulePrivate("audit").check(production);
    }

    @Test
    void theSharedKernelKnowsNothingAboutAnyFeature() {
        noClasses()
                .that().resideInAPackage(ROOT + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".user..", ROOT + ".auth..", ROOT + ".article..", ROOT + ".audit..")
                .because("the shared kernel must stay independent of the features that use it")
                .check(production);
    }

    @Test
    void domainCodeDoesNotDependOnTheWebLayer() {
        noClasses()
                .that().resideInAPackage(ROOT + "..domain..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + "..web..")
                .because("domain rules must be testable without an HTTP request in sight")
                .check(production);
    }

    @Test
    void controllersLiveInWebPackagesOnly() {
        classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAPackage(ROOT + "..web..")
                .because("finding an endpoint should be a matter of knowing the module, not grepping")
                .check(production);
    }

    @Test
    void entitiesLiveInDomainPackagesOnly() {
        classes()
                .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAPackage(ROOT + "..domain..")
                .because("persistence belongs to the domain, not to a controller or a DTO package")
                .check(production);
    }

    @Test
    void theAuthModuleTalksToAccountsThroughTheFacadeAndNothingElse() {
        noClasses()
                .that().resideInAPackage(ROOT + ".auth..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".user.internal..")
                .because("auth must not see the password hash, the entity, or the repository")
                .check(production);
    }

    @Test
    void noModuleDependsOnAnotherModulesWebLayer() {
        noClasses()
                .that().resideInAPackage(ROOT + ".auth..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".article.web..")
                .check(production);

        noClasses()
                .that().resideInAPackage(ROOT + ".article..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".user.web..")
                .check(production);
    }
}
