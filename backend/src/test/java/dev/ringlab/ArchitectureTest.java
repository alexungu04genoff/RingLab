package dev.ringlab;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import dev.ringlab.domain.build.ranking.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

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

    private static final ArchRule persistence_does_not_own_ranking = classes()
            .that().resideInAnyPackage("..adapter.out.db..")
            .should(onlyRetrieveRankingFacts());

    private static ArchCondition<JavaClass> onlyRetrieveRankingFacts() {
        return new ArchCondition<>("retrieve candidate facts without executing ranking or sorting") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                for (var dependency : type.getDirectDependenciesFromSelf()) {
                    String target = dependency.getTargetClass().getName();
                    // The enclosing class can appear as metadata for its nested Candidate record.
                    if (target.startsWith("dev.ringlab.domain.build.ranking.")
                            && !target.equals(BuildRanking.Candidate.class.getName())
                            && !target.equals(BuildRanking.class.getName())) {
                        events.add(SimpleConditionEvent.violated(type, dependency.getDescription()));
                    }
                }
                for (var call : type.getMethodCallsFromSelf()) {
                    String owner = call.getTarget().getOwner().getName();
                    String method = call.getTarget().getName();
                    // Private bookmark chronology is database pagination, not public build ranking.
                    // This exception permits only Criteria ordering in this adapter; ranking policy
                    // dependencies, comparators and local sorting remain forbidden everywhere.
                    boolean bookmarkChronology = type.getName().equals(
                            "dev.ringlab.adapter.out.db.build.SavedBuildDbAdapter")
                            && owner.equals("jakarta.persistence.criteria.CriteriaQuery") && method.equals("orderBy");
                    if (owner.equals(BuildRanking.class.getName())
                            || owner.equals(Comparator.class.getName())
                            || (Set.of("sort", "sorted", "orderBy").contains(method) && !bookmarkChronology)) {
                        events.add(SimpleConditionEvent.violated(type, call.getDescription()));
                    }
                }
            }
        };
    }

    @Test
    void ranking_guard_allows_projection_but_rejects_policy_and_local_sorting() {
        assertFalse(checkFixture(CandidateProjection.class));
        assertTrue(checkFixture(WilsonPolicy.class));
        assertTrue(checkFixture(CanonicalPolicy.class));
        assertTrue(checkFixture(LocalSorting.class));
    }

    private boolean checkFixture(Class<?> fixture) {
        return classes().should(onlyRetrieveRankingFacts())
                .evaluate(new ClassFileImporter().importClasses(fixture)).hasViolation();
    }

    static class CandidateProjection {
        BuildRanking.Candidate project(UUID id, Instant createdAt) {
            return new BuildRanking.Candidate(id, createdAt);
        }
    }
    static class WilsonPolicy {
        double score() { return WilsonScore.lowerBound(3, 0); }
    }
    static class CanonicalPolicy {
        Object comparator() { return BuildRanking.comparator(BuildSort.NEWEST, Map.of(), Map.of()); }
    }
    static class LocalSorting {
        Object sort(List<String> values) { return values.stream().sorted().toList(); }
    }
}
