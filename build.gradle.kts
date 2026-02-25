/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import java.lang.System.getenv
import java.net.URI

plugins {
    java
    val kotlinVersion = "2.3.10"
    val helmVersion = "3.1.1"
    id("org.springframework.boot") version "3.5.11"
    id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("dev.yumi.gradle.licenser") version "2.2.2"

    id("io.github.build-extensions-oss.helm") version helmVersion
    id("io.github.build-extensions-oss.helm-publish") version helmVersion
    id("net.researchgate.release") version "3.1.0"
    id("com.vanniktech.maven.publish") version "0.36.0"
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion
}

group = "org.eclipse.lmos"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

ktlint {
    version.set("1.5.0")
}

license {
    rule(file("LICENSE"))
    include("**/*.java")
    include("**/*.kt")
    include("**/*.yaml")
    exclude("**/*.properties")
}

fun getProperty(propertyName: String) = System.getenv(propertyName) ?: project.findProperty(propertyName) as String

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
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
            sourceDir.set(file("src/main/helm"))
        }
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
    val operatorFrameworkVersion = "6.2.0"
    val fabric8Version = "7.5.2"
    val testcontainersVersion = "1.21.4"
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("io.javaoperatorsdk:operator-framework-spring-boot-starter:$operatorFrameworkVersion")
    implementation("io.fabric8:generator-annotations:$fabric8Version")

    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.semver4j:semver4j:6.0.0")

    testImplementation("io.javaoperatorsdk:operator-framework-spring-boot-starter-test:$operatorFrameworkVersion") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
    }

    implementation("org.eclipse.lmos:lmos-classifier-vector-spring-boot-starter:0.24.0")
    implementation("io.fabric8:generator-annotations:$fabric8Version")
    kapt("io.fabric8:crd-generator-apt:$fabric8Version")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("org.awaitility:awaitility:4.3.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:k3s:$testcontainersVersion")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.83")
    testImplementation("io.mockk:mockk:1.14.9")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.annotationProcessorPath = configurations["kapt"]
}

kapt {
    arguments {
        arg("crd.output.dir", "src/main/resources/META-INF/fabric8")
    }
}
