plugins {
	java
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "0.11.5"
}

group = "io.github.samzhu"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.0.5"

dependencies {
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springaicommunity:agent-sandbox-core:0.9.1")
	implementation("org.testcontainers:testcontainers:1.20.4")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:junit-jupiter:1.20.4")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// IT 類別（*IT.java）由專用任務執行，不在 ./gradlew test 中跑
tasks.test {
	exclude("**/*IT.class")
}

// 整合測試任務：./gradlew integrationTest -Dgrimo.it.docker=true
tasks.register<Test>("integrationTest") {
	description = "Runs integration tests (*IT.java)"
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform()
	include("**/*IT.class")
	shouldRunAfter(tasks.test)
	// 傳遞 grimo.* 系統屬性至測試 JVM（例如 -Dgrimo.it.docker=true）
	System.getProperties().forEach { k, v ->
		if (k.toString().startsWith("grimo.")) {
			systemProperty(k.toString(), v)
		}
	}
}

graalvmNative {
	binaries {
		named("main") {
			imageName.set("grimo")
			buildArgs.add("--no-fallback")
		}
	}
	metadataRepository {
		enabled.set(true)
	}
}