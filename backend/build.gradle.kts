plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
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
	maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
	imports {
		mavenBom("io.github.markpollack:agentworks-bom:1.0.12") // https://central.sonatype.com/artifact/io.github.markpollack/agentworks-bom
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	runtimeOnly("org.xerial:sqlite-jdbc")

	testImplementation("io.github.markpollack:workflow-batch")
	testImplementation("io.github.markpollack:workflow-temporal")
	testImplementation("io.github.markpollack:journal-core")
	testImplementation("io.github.markpollack:memory-core:0.1.0")
	testImplementation("io.github.markpollack:agent-hooks-core")
	testImplementation("io.github.markpollack:agent-client-core")
	testImplementation("io.github.markpollack:agent-judge-core")
	testImplementation("io.github.markpollack:agent-judge-exec")
	testImplementation("io.github.markpollack:agent-judge-file")
	testImplementation("io.github.markpollack:agent-sandbox-core")
	testImplementation("io.github.markpollack:experiment-core:0.2.0")
	testImplementation("com.agentclientprotocol:acp-core")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
	testRuntimeOnly("org.hibernate.orm:hibernate-community-dialects")
	testRuntimeOnly("org.xerial:sqlite-jdbc")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
