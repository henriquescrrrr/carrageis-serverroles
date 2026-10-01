plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "pt.henrique.serverroles"
version = "2.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.140-beta")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "pt.henrique.serverroles.libs.hikari")
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.named("runServer") {
    // Configure the local test server here if needed.
    // e.g. (this as xyz.jpenilla.runtask.task.RunServer).minecraftVersion("26.3")
}
