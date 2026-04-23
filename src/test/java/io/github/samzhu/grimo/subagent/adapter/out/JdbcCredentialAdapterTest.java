package io.github.samzhu.grimo.subagent.adapter.out;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import io.github.samzhu.grimo.subagent.domain.Credential;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCredentialAdapterTest {

    private static Connection connection;
    private static JdbcTemplate jdbc;
    private JdbcCredentialAdapter adapter;

    @BeforeAll
    static void initDb() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:credential_adapter_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM grimo_credential");
        adapter = new JdbcCredentialAdapter(jdbc);
    }

    @Test
    @DisplayName("[S030] save and findById — credential round-trips correctly")
    void saveAndFindById() {
        // Given
        var now = Instant.now();
        var expiresAt = now.plus(365, ChronoUnit.DAYS);
        var credential = new Credential("cred1234abcd", "personal-max", "claude",
                "oauth_token", "sk-ant-oat01-test-token", 1, expiresAt, now);

        // When
        adapter.save(credential);
        var found = adapter.findById("cred1234abcd");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("cred1234abcd");
        assertThat(found.get().label()).isEqualTo("personal-max");
        assertThat(found.get().provider()).isEqualTo("claude");
        assertThat(found.get().credentialType()).isEqualTo("oauth_token");
        assertThat(found.get().secretValue()).isEqualTo("sk-ant-oat01-test-token");
        assertThat(found.get().sortOrder()).isEqualTo(1);
        assertThat(found.get().expiresAt()).isNotNull();
        assertThat(found.get().createdAt()).isNotNull();
    }

    @Test
    @DisplayName("[S030] save with null expiresAt — API key credential")
    void saveWithNullExpiresAt() {
        // Given
        var credential = new Credential("cred5678efgh", "api-backup", "claude",
                "api_key", "sk-ant-api03-test", 3, null, Instant.now());

        // When
        adapter.save(credential);
        var found = adapter.findById("cred5678efgh");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().expiresAt()).isNull();
        assertThat(found.get().credentialType()).isEqualTo("api_key");
    }

    @Test
    @DisplayName("[S030] findAll returns credentials ordered by sort_order ASC")
    void findAllOrdered() {
        // Given
        var now = Instant.now();
        adapter.save(new Credential("credBBBB", "second", "claude", "oauth_token",
                "tok2", 2, null, now));
        adapter.save(new Credential("credAAAA", "first", "claude", "oauth_token",
                "tok1", 1, null, now));

        // When
        var all = adapter.findAll();

        // Then
        assertThat(all).hasSize(2);
        assertThat(all.get(0).sortOrder()).isEqualTo(1);
        assertThat(all.get(1).sortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("[S030] findByProviderOrderBySortOrder filters by provider")
    void findByProvider() {
        // Given
        var now = Instant.now();
        adapter.save(new Credential("credClaude", "claude-tok", "claude", "oauth_token",
                "tok1", 1, null, now));
        adapter.save(new Credential("credGemini", "gemini-key", "gemini", "api_key",
                "gem1", 1, null, now));

        // When
        var claudeCreds = adapter.findByProviderOrderBySortOrder("claude");
        var geminiCreds = adapter.findByProviderOrderBySortOrder("gemini");

        // Then
        assertThat(claudeCreds).hasSize(1);
        assertThat(claudeCreds.getFirst().provider()).isEqualTo("claude");
        assertThat(geminiCreds).hasSize(1);
        assertThat(geminiCreds.getFirst().provider()).isEqualTo("gemini");
    }

    @Test
    @DisplayName("[S030] deleteById removes credential")
    void deleteById() {
        // Given
        adapter.save(new Credential("credDEL", "to-delete", "claude", "oauth_token",
                "tok", 1, null, Instant.now()));
        assertThat(adapter.findById("credDEL")).isPresent();

        // When
        adapter.deleteById("credDEL");

        // Then
        assertThat(adapter.findById("credDEL")).isEmpty();
    }

    @Test
    @DisplayName("[S030] save updates existing credential (MERGE)")
    void saveUpdatesExisting() {
        // Given
        var now = Instant.now();
        adapter.save(new Credential("credUPD", "original", "claude", "oauth_token",
                "tok-old", 1, null, now));

        // When — save with same id but different sort_order
        adapter.save(new Credential("credUPD", "original", "claude", "oauth_token",
                "tok-old", 5, null, now));

        // Then
        var found = adapter.findById("credUPD");
        assertThat(found).isPresent();
        assertThat(found.get().sortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("[S030] findById returns empty for nonexistent ID")
    void findByIdEmpty() {
        assertThat(adapter.findById("nonexistent12")).isEmpty();
    }
}
