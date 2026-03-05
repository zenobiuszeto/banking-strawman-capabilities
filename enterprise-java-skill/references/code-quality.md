# Code Quality Reference — SonarQube, Checkstyle, SpotBugs, ArchUnit, JaCoCo

## 1. Gradle Quality Configuration

```groovy
// build.gradle
plugins {
    id 'checkstyle'
    id 'com.github.spotbugs' version '6.0.9'
    id 'jacoco'
    id 'org.sonarqube' version '5.0.0.4638'
}

checkstyle {
    toolVersion = '10.17.0'
    configFile = file("${rootProject.projectDir}/config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

spotbugs {
    toolVersion = '4.8.6'
    effort = 'max'
    reportLevel = 'medium'
    excludeFilter = file("${rootProject.projectDir}/config/spotbugs/exclude.xml")
}

jacoco {
    toolVersion = '0.8.12'
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80   // 80% line coverage minimum
            }
        }
    }
}

sonarqube {
    properties {
        property 'sonar.projectKey', "${project.group}:${project.name}"
        property 'sonar.projectName', project.name
        property 'sonar.coverage.jacoco.xmlReportPaths', "${buildDir}/reports/jacoco/test/jacocoTestReport.xml"
        property 'sonar.java.checkstyle.reportPaths', "${buildDir}/reports/checkstyle/main.xml"
        property 'sonar.qualitygate.wait', 'true'
    }
}

check.dependsOn jacocoTestCoverageVerification
```

## 2. ArchUnit — Enforce Architecture

```java
@AnalyzeClasses(packages = "com.yourorg")
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersMustNotDependOnRepositories =
        noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule servicesMustNotDependOnControllers =
        noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule repositoriesMustOnlyBeAccessedFromServices =
        classes().that().resideInAPackage("..repository..")
            .should().onlyBeAccessed().byAnyPackage("..service..", "..repository..");

    @ArchTest
    static final ArchRule noFieldInjection =
        noFields().should().beAnnotatedWith(Autowired.class)
            .because("Use constructor injection with @RequiredArgsConstructor");

    @ArchTest
    static final ArchRule controllersShouldBeAnnotatedCorrectly =
        classes().that().resideInAPackage("..controller..")
            .should().beAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule noSystemOutPrintln =
        noClasses().should().callMethod(System.class, "println", String.class)
            .because("Use @Slf4j logger instead of System.out.println");
}
```

## 3. Checkstyle Configuration Highlights

```xml
<!-- config/checkstyle/checkstyle.xml -->
<module name="Checker">
    <module name="TreeWalker">
        <!-- No wildcard imports -->
        <module name="AvoidStarImport"/>
        <!-- Max method length: 40 lines -->
        <module name="MethodLength">
            <property name="max" value="40"/>
        </module>
        <!-- Max class length: 300 lines -->
        <module name="FileLength">
            <property name="max" value="300"/>
        </module>
        <!-- Naming conventions -->
        <module name="ConstantName"/>
        <module name="LocalVariableName"/>
        <module name="MethodName"/>
        <!-- Javadoc on public methods -->
        <module name="JavadocMethod">
            <property name="scope" value="public"/>
        </module>
    </module>
</module>
```

## 4. Quality Gate Definition (SonarQube)

Configure in SonarQube UI or as code:
- Coverage on new code >= 80%
- Duplicated lines on new code < 3%
- Maintainability rating = A
- Reliability rating = A
- Security rating = A
- Security hotspots reviewed = 100%

## 5. Pre-commit Hooks

```bash
#!/bin/sh
# .git/hooks/pre-commit
./gradlew checkstyleMain spotbugsMain --daemon
if [ $? -ne 0 ]; then
    echo "Quality checks failed. Commit aborted."
    exit 1
fi
```

Or with Lefthook/Husky for team-wide enforcement.

## 6. Quality Checklist

- [ ] JaCoCo coverage >= 80% enforced in CI (`jacocoTestCoverageVerification`)
- [ ] Checkstyle violations = 0 (`maxWarnings = 0`)
- [ ] SpotBugs at `effort = max`, `reportLevel = medium`
- [ ] ArchUnit tests enforce layering rules and no field injection
- [ ] SonarQube quality gate configured and `qualitygate.wait=true` in CI
- [ ] Pre-commit hooks prevent committing broken code locally
