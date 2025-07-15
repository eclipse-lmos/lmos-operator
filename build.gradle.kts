/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.vanniktech.maven.publish.SonatypeHost
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import java.lang.System.getenv
import java.net.URI

plugins {
    java
    val kotlinVersion = "2.1.20"
    id("org.springframework.boot") version "3.4.5"
    id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.cadixdev.licenser") version "0.6.1"

    id("com.citi.helm") version "2.2.0"
    id("com.citi.helm-publish") version "2.2.0"
    id("net.researchgate.release") version "3.1.0"
    id("com.vanniktech.maven.publish") version "0.34.0"
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion
}

group = "org.eclipse.lmos"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

ktlint {
    version.set("1.5.0")
}

license {
    include("**/*.java")
    include("**/*.kt")
    include("**/*.yaml")
    exclude("**/*.properties")
}

fun getProperty(propertyName: String) = System.getenv(propertyName) ?: project.findProperty(propertyName) as String

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
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
        newVersionCommitMessage = "New Snapshot-Version:"
        preTagCommitMessage = "Release:"
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

tasks.named<BootBuildImage>("bootBuildImage") {
    group = "publishing"
    if ((System.getenv("REGISTRY_URL") ?: project.findProperty("REGISTRY_URL")) != null) {
        val registryUrl = getProperty("REGISTRY_URL")
        val registryUsername = getProperty("REGISTRY_USERNAME")
        val registryPassword = getProperty("REGISTRY_PASSWORD")
        val registryNamespace = getProperty("REGISTRY_NAMESPACE")

        imageName.set("$registryUrl/$registryNamespace/${rootProject.name}:${project.version}")
        publish = true
        docker {
            publishRegistry {
                url.set(registryUrl)
                username.set(registryUsername)
                password.set(registryPassword)
            }
        }
    } else {
        imageName.set("${rootProject.name}:${project.version}")
        publish = false
    }
}

helm {
    charts {
        create("main") {
            chartName.set("${rootProject.name}-chart")
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
    group = "publishing"
    dependsOn(tasks.named("helmPackageMainChart"))

    doLast {
        if ((System.getenv("REGISTRY_URL") ?: project.findProperty("REGISTRY_URL")) != null) {
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
                args(
                    tasks
                        .named("helmPackageMainChart")
                        .get()
                        .outputs.files.singleFile
                        .toString(),
                )
                args("oci://$registryUrl/$registryNamespace")
            }

            helm.execHelm("registry", "logout") {
                args(registryUrl)
            }
        }
    }
}

tasks.named("publish") {
    dependsOn(tasks.named<BootBuildImage>("bootBuildImage"))
    dependsOn(tasks.named("helmPush"))
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
    implementation("io.fabric8:generator-annotations:7.3.1")

    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin")

    implementation("org.semver4j:semver4j:5.6.0")

    testImplementation("io.javaoperatorsdk:operator-framework-spring-boot-starter-test:5.6.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
    }

    implementation("io.fabric8", "generator-annotations", "7.3.1")
    kapt("io.fabric8", "crd-generator-apt", "6.13.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("org.awaitility:awaitility:4.3.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:k3s:1.21.1")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:1.70")
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
