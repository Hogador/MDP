plugins {
    kotlin("jvm") version "2.0.21"
    id("io.ktor.plugin") version "3.1.2"
    kotlin("plugin.serialization") version "2.0.21"
}

application {
    mainClass.set("com.mdaopay.paymaster.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.1.2")
    implementation("io.ktor:ktor-server-netty:3.1.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("io.ktor:ktor-client-core:3.1.2")
    implementation("io.ktor:ktor-client-cio:3.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-server-auth:3.1.2")
    implementation("org.web3j:core:4.12.3")
    implementation("org.web3j:crypto:4.12.3")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.lettuce:lettuce-core:6.5.3.RELEASE")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.flywaydb:flyway-core:10.22.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.22.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.ktor:ktor-server-test-host:3.1.2")
    testImplementation("io.ktor:ktor-client-mock:3.1.2")
}

kotlin {
    jvmToolchain(17)
}

// Workaround: web3j 4.14.0 requires JVM 21+, but we only have JDK 17/26.
// JDK 26 incompatible with Kotlin 2.0.21. Use JDK 17 and accept web3j JVM
// compatibility warning. Currently using JDK 17 which works at runtime.
// TODO: Install JDK 21 or downgrade web3j when available.

// dependencyLocking disabled for build compatibility
// dependencyLocking {
//     lockAllConfigurations()
// }

tasks.test {
    useJUnitPlatform()
}
