package io.github.samzhu.grimo.subagent.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.samzhu.grimo.subagent.application.port.out.CredentialPort;
import io.github.samzhu.grimo.subagent.application.port.out.SettingPort;
import io.github.samzhu.grimo.subagent.domain.Credential;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialResolverServiceTest {

    private StubCredentialPort credentialPort;
    private StubSettingPort settingPort;
    private CredentialResolverService resolver;

    @BeforeEach
    void setUp() {
        credentialPort = new StubCredentialPort();
        settingPort = new StubSettingPort();
        resolver = new CredentialResolverService(credentialPort, settingPort);
    }

    @Test
    @DisplayName("[S030] AC-3: PRIORITY strategy returns lowest sort_order unexpired credential")
    void ac3_priorityStrategy() {
        // Given
        settingPort.set("credential-strategy", "PRIORITY");
        var now = Instant.now();
        var cred1 = new Credential("cred1", "personal-max", "claude", "oauth_token",
                "sk-ant-oat01-aaa", 1, now.plus(365, ChronoUnit.DAYS), now);
        var cred2 = new Credential("cred2", "work-team", "claude", "oauth_token",
                "sk-ant-oat01-bbb", 2, now.plus(365, ChronoUnit.DAYS), now);
        credentialPort.save(cred1);
        credentialPort.save(cred2);

        // When
        var result = resolver.resolve("claude");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("cred1");
        assertThat(result.get().sortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("[S030] AC-4: RANDOM strategy distributes across all unexpired credentials")
    void ac4_randomStrategy() {
        // Given
        settingPort.set("credential-strategy", "RANDOM");
        var now = Instant.now();
        var future = now.plus(365, ChronoUnit.DAYS);
        credentialPort.save(new Credential("cred1", "a", "claude", "oauth_token", "tok1", 1, future, now));
        credentialPort.save(new Credential("cred2", "b", "claude", "oauth_token", "tok2", 2, future, now));
        credentialPort.save(new Credential("cred3", "c", "claude", "oauth_token", "tok3", 3, future, now));

        // When — call 100 times
        var selectedIds = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            resolver.resolve("claude").ifPresent(c -> selectedIds.add(c.id()));
        }

        // Then — all 3 credentials should be selected at least once
        assertThat(selectedIds).containsExactlyInAnyOrder("cred1", "cred2", "cred3");
    }

    @Test
    @DisplayName("[S030] AC-5: expired credentials are automatically skipped")
    void ac5_expiredSkipped() {
        // Given
        settingPort.set("credential-strategy", "PRIORITY");
        var now = Instant.now();
        var expired = new Credential("cred1", "expired-one", "claude", "oauth_token",
                "sk-ant-oat01-old", 1, now.minus(1, ChronoUnit.DAYS), now);
        var valid = new Credential("cred2", "valid-one", "claude", "oauth_token",
                "sk-ant-oat01-new", 2, now.plus(365, ChronoUnit.DAYS), now);
        credentialPort.save(expired);
        credentialPort.save(valid);

        // When
        var result = resolver.resolve("claude");

        // Then — skips expired, returns valid
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("cred2");
    }

    @Test
    @DisplayName("[S030] AC-9: default strategy is PRIORITY when not set")
    void ac9_defaultStrategy() {
        // Given — no strategy setting
        var now = Instant.now();
        var cred1 = new Credential("cred1", "first", "claude", "oauth_token",
                "tok1", 1, now.plus(365, ChronoUnit.DAYS), now);
        var cred2 = new Credential("cred2", "second", "claude", "oauth_token",
                "tok2", 2, now.plus(365, ChronoUnit.DAYS), now);
        credentialPort.save(cred1);
        credentialPort.save(cred2);

        // When
        var result = resolver.resolve("claude");

        // Then — PRIORITY is default, returns sort_order=1
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("cred1");
    }

    @Test
    @DisplayName("[S030] resolve returns empty when no credentials exist")
    void resolveEmptyPool() {
        // Given — empty pool

        // When
        var result = resolver.resolve("claude");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[S030] resolve returns empty when all credentials are expired")
    void resolveAllExpired() {
        // Given
        var now = Instant.now();
        credentialPort.save(new Credential("cred1", "old", "claude", "oauth_token",
                "tok1", 1, now.minus(1, ChronoUnit.DAYS), now));

        // When
        var result = resolver.resolve("claude");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[S030] resolve filters by provider")
    void resolveFiltersByProvider() {
        // Given
        var now = Instant.now();
        var future = now.plus(365, ChronoUnit.DAYS);
        credentialPort.save(new Credential("cred1", "gemini-key", "gemini", "api_key",
                "gem-key", 1, null, now));
        credentialPort.save(new Credential("cred2", "claude-tok", "claude", "oauth_token",
                "sk-ant-tok", 1, future, now));

        // When
        var result = resolver.resolve("claude");

        // Then — only returns claude credentials
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("cred2");
    }

    // --- Stubs ---

    static class StubCredentialPort implements CredentialPort {
        private final List<Credential> store = new ArrayList<>();

        @Override
        public void save(Credential credential) {
            store.removeIf(c -> c.id().equals(credential.id()));
            store.add(credential);
        }
        @Override
        public Optional<Credential> findById(String id) {
            return store.stream().filter(c -> c.id().equals(id)).findFirst();
        }
        @Override
        public List<Credential> findAll() {
            return store.stream()
                    .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
                    .toList();
        }
        @Override
        public List<Credential> findByProviderOrderBySortOrder(String provider) {
            return store.stream()
                    .filter(c -> c.provider().equals(provider))
                    .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
                    .toList();
        }
        @Override
        public void deleteById(String id) {
            store.removeIf(c -> c.id().equals(id));
        }
    }

    static class StubSettingPort implements SettingPort {
        private final Map<String, String> settings = new HashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(settings.get(key));
        }
        @Override
        public void set(String key, String value) {
            settings.put(key, value);
        }
    }
}
