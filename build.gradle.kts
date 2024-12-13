/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.vanniktech.maven.publish.SonatypeHost
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import java.lang.System.getenv
import java.net.URI

plugins {
    java
    id("org.springframework.boot") version "3.4.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.cadixdev.licenser") version "0.6.1"

    id("com.citi.helm") version "2.2.0"
    id("com.citi.helm-publish") version "2.2.0"
    id("net.researchgate.release") version "3.0.2"
    id("com.vanniktech.maven.publish") version "0.30.0"
    kotlin("jvm")
    kotlin("kapt") version "2.0.21"
}

group = "org.eclipse.lmos"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

license {
    include("**/*.java")
    include("**/*.kt")
    include("**/*.yaml")
    exclude("**/*.properties")
}

helm {
    charts {
        create("main") {
            chartName.set("${project.name}-chart")
            chartVersion.set("${project.version}")
            sourceDir.set(file("src/main/helm"))
        }
    }
}

tasks.register("replaceChartVersion") {
    doLast {
        val chartFile = file("src/main/helm/Chart.yaml")
        val content = chartFile.readText()
        val updatedContent = content.replace("\${chartVersion}", "${project.version}")
        chartFile.writeText(updatedContent)
    }
}

tasks.register("helmPush") {
    description = "Push Helm chart to OCI registry"
    group = "helm"
    dependsOn(tasks.named("helmPackageMainChart"))

    doLast {
        val registryUrl = getProperty("REGISTRY_URL")
        val registryUsername = getProperty("REGISTRY_USERNAME")
        val registryPassword = getProperty("REGISTRY_PASSWORD")
        val registryNamespace = getProperty("REGISTRY_NAMESPACE")

        helm.execHelm("registry", "login") {
            option("-u", registryUsername)
            option("-p", registryPassword)
            args(registryUrl)
        }

        helm.execHelm("push") {
            args(tasks.named("helmPackageMainChart").get().outputs.files.singleFile.toString())
            args("oci://$registryUrl/$registryNamespace")
        }

        helm.execHelm("registry", "logout") {
            args(registryUrl)
        }
    }
}

fun getProperty(propertyName: String) = getenv(propertyName) ?: project.findProperty(propertyName) as String

tasks.named<BootBuildImage>("bootBuildImage") {
    val registryUrl = getProperty("REGISTRY_URL")
    val registryUsername = getProperty("REGISTRY_USERNAME")
    val registryPassword = getProperty("REGISTRY_PASSWORD")
    val registryNamespace = getProperty("REGISTRY_NAMESPACE")

    imageName.set("$registryUrl/$registryNamespace/${project.name}:${project.version}")
    publish = true
    docker {
        publishRegistry {
            url.set(registryUrl)
            username.set(registryUsername)
            password.set(registryPassword)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name = "LMOS Operator"
        description =
            """The LMOS Operator is a Kubernetes operator designed to dynamically resolve Channel requirements based on 
                the capabilities of installed Agents within a Kubernetes cluster (environment).
            """.trimMargin()
        url = "https://github.com/eclipse-lmos/lmos-operator"
        licenses {
            license {
                name = "Apache-2.0"
                distribution = "repo"
                url = "https://github.com/eclipse-lmos/lmos-operator/blob/main/LICENSES/Apache-2.0.txt"
            }
        }
        developers {
            developer {
                id = "telekom"
                name = "Telekom Open Source"
                email = "opensource@telekom.de"
            }
        }
        scm {
            url = "https://github.com/eclipse-lmos/lmos-operator.git"
        }
    }

    release {
        buildTasks = listOf("releaseBuild")
        ignoredSnapshotDependencies =
            listOf()
        newVersionCommitMessage = "New Snapshot-Version:"
        preTagCommitMessage = "Release:"
    }

    tasks.register("releaseBuild") {
        dependsOn(subprojects.mapNotNull { it.tasks.findByName("build") })
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = URI("https://maven.pkg.github.com/eclipse-lmos/lmos-operator")
            credentials {
                username = findProperty("GITHUB_USER")?.toString() ?: getenv("GITHUB_USER")
                password = findProperty("GITHUB_TOKEN")?.toString() ?: getenv("GITHUB_TOKEN")
            }
        }
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("io.javaoperatorsdk:operator-framework-spring-boot-starter:5.6.0")
    implementation("io.fabric8:generator-annotations:6.13.4")

    implementation("org.apache.felix:org.apache.felix.resolver:2.0.4")

    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("io.sentry:sentry-logback:7.11.0")

    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin")

    implementation("org.semver4j:semver4j:5.4.1")

    testImplementation("io.javaoperatorsdk:operator-framework-spring-boot-starter-test:5.6.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
    }

    implementation("io.fabric8", "generator-annotations", "6.13.4")
    kapt("io.fabric8", "crd-generator-apt", "6.13.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("org.awaitility:awaitility:4.2.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.annotationProcessorPath = configurations["kapt"]
}

kapt {
    arguments {
        arg("crd.output.dir", file("src/main/resources/META-INF/fabric8"))
    }
}
