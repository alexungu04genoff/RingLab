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
        ports_depend_only_on_jdk_or_domain.check(classes);
        inbound_adapters_use_input_ports.check(classes);
        outbound_adapters_do_not_depend_on_input_ports.check(classes);
        adapters_do_not_execute_recommendations.check(classes);
        noClasses().that().resideInAnyPackage("..adapter..", "..application.build.recommendation..")
                .should().dependOnClassesThat().haveFullyQualifiedName("dev.ringlab.domain.gamedata.ScenarioStatsCalculator")
                .check(classes);
        noClasses().that().resideInAPackage("..domain.build.recommendation..")
                .and().doNotHaveSimpleName("RecommendationStatsEvaluator")
                .should().dependOnClassesThat().haveFullyQualifiedName("dev.ringlab.domain.gamedata.ScenarioStatsCalculator")
                .check(classes);
        noClasses().that().haveSimpleName("ScenarioStatsService").should().dependOnClassesThat()
                .resideInAnyPackage("..domain.build.recommendation..", "..domain.collection..", "..adapter..")
                .check(classes);
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

    private static final ArchRule ports_depend_only_on_jdk_or_domain = classes()
            .that().resideInAnyPackage("..port..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "dev.ringlab.domain..", "dev.ringlab.port..");

    private static final Set<String> transportApplicationErrors = Set.of(
            "dev.ringlab.application.AppException",
            "dev.ringlab.application.AlreadyExistsException",
            "dev.ringlab.application.AuthenticationException",
            "dev.ringlab.application.ExternalServiceUnavailableException",
            "dev.ringlab.application.ForbiddenException",
            "dev.ringlab.application.NotFoundException",
            "dev.ringlab.application.ValidationException");

    private static final ArchRule inbound_adapters_use_input_ports = classes()
            .that().resideInAnyPackage("dev.ringlab.adapter.in..")
            .should(useApplicationBoundary());

    private static ArchCondition<JavaClass> useApplicationBoundary() {
        return new ArchCondition<>("invoke application capabilities through input ports") {
            @Override public void check(JavaClass type, ConditionEvents events) {
                for (var dependency : type.getDirectDependenciesFromSelf()) {
                    var target = dependency.getTargetClass();
                    boolean applicationImplementation = target.getPackageName().startsWith("dev.ringlab.application")
                            && !transportApplicationErrors.contains(target.getName());
                    if (applicationImplementation || target.getPackageName().startsWith("dev.ringlab.port.out"))
                        events.add(SimpleConditionEvent.violated(type, dependency.getDescription()));
                }
            }
        };
    }

    private static final ArchRule outbound_adapters_do_not_depend_on_input_ports = noClasses()
            .that().resideInAnyPackage("dev.ringlab.adapter.out..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.ringlab.port.in..");

    @Test
    void inbound_guard_rejects_implementation_and_repository_shortcuts() {
        assertTrue(inboundFixtureViolates(DirectBuildService.class));
        assertTrue(inboundFixtureViolates(DirectBuildRepository.class));
        assertFalse(inboundFixtureViolates(BuildInputPort.class));
        assertFalse(inboundFixtureViolates(TransportValidation.class));
    }

    @Test
    void application_and_outbound_collaboration_still_use_their_own_boundaries() {
        var application = new ClassFileImporter().importClasses(dev.ringlab.application.build.BuildService.class,
                dev.ringlab.application.build.BuildDraftValidator.class);
        application_does_not_depend_on_adapters_or_rest_or_persistence.check(application);
        assertTrue(application.get(dev.ringlab.application.build.BuildService.class)
                .getDirectDependenciesFromSelf().stream().anyMatch(d -> d.getTargetClass().isEquivalentTo(
                        dev.ringlab.port.out.BuildRepository.class)));
        assertTrue(application.get(dev.ringlab.application.build.BuildDraftValidator.class)
                .getDirectDependenciesFromSelf().stream().anyMatch(d -> d.getTargetClass().isEquivalentTo(
                        dev.ringlab.application.validation.ProfanityPolicy.class)));
        var outbound = new ClassFileImporter().importClasses(dev.ringlab.adapter.out.db.build.BuildDbAdapter.class);
        outbound_adapters_do_not_depend_on_input_ports.check(outbound);
        assertTrue(outbound.get(dev.ringlab.adapter.out.db.build.BuildDbAdapter.class)
                .getDirectDependenciesFromSelf().stream().anyMatch(d -> d.getTargetClass().isEquivalentTo(
                        dev.ringlab.port.out.BuildRepository.class)));
    }

    private static boolean inboundFixtureViolates(Class<?> fixture) {
        return classes().should(useApplicationBoundary())
                .evaluate(new ClassFileImporter().importClasses(fixture)).hasViolation();
    }

    static class DirectBuildService {
        Object get(dev.ringlab.application.build.BuildService service, UUID id) { return service.get(id); }
    }
    static class DirectBuildRepository {
        Object get(dev.ringlab.port.out.BuildRepository repository, UUID id) { return repository.find(id); }
    }
    static class BuildInputPort {
        Object get(dev.ringlab.port.in.BuildUseCase builds, UUID id) { return builds.get(id); }
    }
    static class TransportValidation {
        void reject() { throw new dev.ringlab.application.ValidationException("Invalid transport input"); }
    }

    private static final ArchRule adapters_do_not_execute_recommendations = classes()
            .that().resideInAnyPackage("..adapter..")
            .should(new ArchCondition<>("map or retrieve recommendation facts without optimizing") {
                @Override public void check(JavaClass type, ConditionEvents events) {
                    for (var call : type.getAccessesFromSelf()) {
                        String owner = call.getTargetOwner().getName();
                        if (owner.equals("dev.ringlab.domain.build.recommendation.BuildRecommendationSolver")
                                || owner.equals("dev.ringlab.domain.build.recommendation.BalancedObjective")
                                || (owner.equals("dev.ringlab.domain.build.recommendation.StatPriority")
                                    && call.getName().equals("compare")))
                            events.add(SimpleConditionEvent.violated(type, call.getDescription()));
                    }
                }
            });

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
