package io.github.samzhu.grimo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ADR-001 的 Spring Boot context smoke test：確認 backend 能用 MVP SQLite
 * datasource 設定啟動。
 *
 * 執行方式：./gradlew test --tests '*GrimoApplicationTests'
 * 通過代表 POC suite 使用的 local-first SQLite datasource 假設可支撐
 * application context 啟動。
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite:${java.io.tmpdir}/grimo-context-loads.sqlite",
		"spring.datasource.driver-class-name=org.sqlite.JDBC",
		"spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class GrimoApplicationTests {

	@Test
	void contextLoads() {
	}

}
