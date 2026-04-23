package io.github.samzhu.grimo.subagent.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.samzhu.grimo.subagent.application.port.out.CredentialPort;
import io.github.samzhu.grimo.subagent.domain.Credential;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialServiceTest {

    private StubCredentialPort credentialPort;
    private CredentialService service;

    @BeforeEach
    void setUp() {
        credentialPort = new StubCredentialPort();
        service = new CredentialService(credentialPort);
    }

    @Test
    @DisplayName("[S030] create assigns auto-incremented sortOrder")
    void createAutoSortOrder() {
        // Given — empty pool

        // When
        var first = service.create("first", "claude", "oauth_token",
                "sk-ant-oat01-aaa", Instant.now().plus(365, ChronoUnit.DAYS));
        var second = service.create("second", "claude", "oauth_token",
                "sk-ant-oat01-bbb", Instant.now().plus(365, ChronoUnit.DAYS));

        // Then
        assertThat(first.sortOrder()).isEqualTo(1);
        assertThat(second.sortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("[S030] create generates unique ID")
    void createGeneratesId() {
        // When
        var cred = service.create("test", "claude", "oauth_token", "tok", null);

        // Then
        assertThat(cred.id()).isNotNull();
        assertThat(cred.id()).hasSize(12);
    }

    @Test
    @DisplayName("[S030] listAll returns all credentials")
    void listAll() {
        // Given
        service.create("a", "claude", "oauth_token", "tok1", null);
        service.create("b", "claude", "oauth_token", "tok2", null);

        // When
        var all = service.listAll();

        // Then
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("[S030] findById returns credential when exists")
    void findById() {
        // Given
        var created = service.create("test", "claude", "oauth_token", "tok", null);

        // When
        var found = service.findById(created.id());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().label()).isEqualTo("test");
    }

    @Test
    @DisplayName("[S030] findById returns empty when not exists")
    void findByIdEmpty() {
        assertThat(service.findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("[S030] delete removes credential")
    void deleteRemoves() {
        // Given
        var created = service.create("test", "claude", "oauth_token", "tok", null);

        // When
        service.delete(created.id());

        // Then
        assertThat(service.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("[S030] updateSortOrder changes sort order")
    void updateSortOrder() {
        // Given
        var created = service.create("test", "claude", "oauth_token", "tok", null);
        assertThat(created.sortOrder()).isEqualTo(1);

        // When
        service.updateSortOrder(created.id(), 5);

        // Then
        var updated = service.findById(created.id());
        assertThat(updated).isPresent();
        assertThat(updated.get().sortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("[S030] updateSortOrder on nonexistent id is no-op")
    void updateSortOrderNonexistent() {
        // When/Then — no exception
        service.updateSortOrder("nonexistent", 5);
    }

    // --- Stub ---

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
}
