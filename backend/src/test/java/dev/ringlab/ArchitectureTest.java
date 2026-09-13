package dev.ringlab;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    @Test
    void layer_dependencies_follow_the_architecture() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.ringlab");

        domain_depends_only_on_jdk_or_domain.check(classes);
        application_does_not_depend_on_adapters_or_rest_or_persistence.check(classes);
        ports_remain_independent_of_application_and_adapters.check(classes);
        persistence_does_not_own_ranking.check(classes);
    }

    private static final ArchRule domain_depends_only_on_jdk_or_domain = classes()
            .that().resideInAnyPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "dev.ringlab.domain..");

    private static final ArchRule application_does_not_depend_on_adapters_or_rest_or_persistence = noClasses()
            .that().resideInAnyPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "jakarta.ws.rs..", "jakarta.persistence..", "org.hibernate..");

    private static final ArchRule ports_remain_independent_of_application_and_adapters = noClasses()
            .that().resideInAnyPackage("..port..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");

    private static final ArchRule persistence_does_not_own_ranking = noClasses()
            .that().resideInAnyPackage("..adapter.out.db..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain.build.ranking..");
}
