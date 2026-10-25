package com.fastdrop.orchestrator;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.fastdrop.orchestrator", importOptions = ImportOption.DoNotIncludeTests.class)
class OrchestratorHexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnFrameworksOrOuterLayers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "..application..",
                            "..infrastructure.."
                    );

    @ArchTest
    static final ArchRule applicationShouldNotDependOnInfrastructureOrSsmImplementation =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..infrastructure..",
                            "org.springframework.statemachine..",
                            "com.fastdrop.statemachine.ssm..",
                            "org.springframework.kafka..",
                            "jakarta.persistence.."
                    );
}
