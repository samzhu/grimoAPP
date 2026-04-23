plugins {
	java
	jacoco
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
extra["springAiVersion"] = "2.0.0-M4"

dependencies {
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-events-jdbc")
	implementation("org.springframework.modulith:spring-modulith-events-jackson")
	implementation("org.springaicommunity:agent-sandbox-core:0.9.1")
	implementation("org.springaicommunity.agents:agent-client-core:0.12.2")
	implementation("org.springaicommunity.agents:agent-model:0.12.2")
	implementation("org.springaicommunity.agents:agent-claude:0.12.2")
	implementation("org.springaicommunity.agents:agent-codex:0.12.2")
	implementation("org.springaicommunity.agents:agent-gemini:0.12.2")
	implementation("org.springaicommunity:spring-ai-agent-utils:0.7.0")
	implementation("org.testcontainers:testcontainers")
	implementation("org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	// §7.7: 轉發 API key 至測試 JVM，供 CLI IT 使用
	listOf("ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY")
		.forEach { k -> System.getenv(k)?.let { environment(k, it) } }
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

// ── JaCoCo coverage gate (qa-strategy.md §2: ≥ 80% line, 全部程式碼扣除純接線) ──

jacoco {
	toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
		csv.required.set(true)
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)

	// 全部程式碼納入，排除無業務邏輯的接線 + 非本專案程式碼：
	//   - *Config.java          @Bean / @ComponentScan 接線
	//   - port/in/, port/out/   純 interface，0 行可執行碼
	//   - package-info.java     模組宣告
	//   - **/events/*           純 Spring event record
	//   - org/springaicommunity 第三方程式碼，非本專案維護範圍
	//   - sandbox/internal      Docker 依賴，待 S008 一併處理（技術債）
	classDirectories.setFrom(
		sourceSets["main"].output.classesDirs.asFileTree.matching {
			exclude(
				"**/*Config.class",
				"**/*Config\$*.class",
				"**/port/in/**",
				"**/port/out/**",
				"**/events/**",
				"**/package-info.class",
				"org/springaicommunity/**",
				"**/sandbox/internal/**"
			)
		}
	)

	violationRules {
		rule {
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.80".toBigDecimal()
			}
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