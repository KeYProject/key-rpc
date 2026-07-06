import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    id("org.jetbrains.dokka")
    jacoco
    id("jacoco-report-aggregation")
    id("test-report-aggregation")
}

repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}


dependencies {
    subprojects.forEach {
        if(it.name != "tools") {
            dokka(it)
            jacocoAggregation(it)
            testReportAggregation(it)
        }
    }
}

reporting {
    reports {
        create<AggregateTestReport>("aggregateTestReport") {
            testSuiteName = "test"
        }
        create<JacocoCoverageReport>("aggregateCoverageReport") {
            testSuiteName = "test"
        }
    }
}

dokka {
    dokkaPublications.html {
        includes.from(file("gradle/index.md"))
        suppressInheritedMembers.set(false)
        suppressObviousFunctions.set(true)
        offlineMode.set(false)
    }
}

jacoco {
    toolVersion = "0.8.15"
}