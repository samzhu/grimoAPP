package io.github.samzhu.grimo.skills.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.samzhu.grimo.skills.application.port.out.SkillStorePort;
import io.github.samzhu.grimo.skills.domain.SkillEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;

/**
 * Unit tests for {@link SkillRegistryService} — S012 AC-1, AC-2, AC-3.
 * Uses mock {@link SkillStorePort}.
 */
class SkillRegistryServiceTest {

    private SkillStorePort store;
    private SkillRegistryService service;

    private static SkillEntry entry(String name, boolean enabled) {
        return new SkillEntry(
                new SkillsTool.Skill("/path/" + name,
                        Map.of("name", name, "description", name + " skill"),
                        "body"),
                enabled);
    }

    @BeforeEach
    void setUp() {
        store = mock(SkillStorePort.class);
        service = new SkillRegistryService(store);
    }

    @Test
    @DisplayName("S012 AC-1: list() delegates to store.loadAll()")
    void listDelegatesToStore() {
        when(store.loadAll()).thenReturn(List.of(entry("hello", true)));

        var result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("hello");
    }

    @Test
    @DisplayName("S012 AC-1: listEnabled() filters disabled skills")
    void listEnabledFiltersDisabled() {
        when(store.loadAll()).thenReturn(List.of(
                entry("hello", true),
                entry("deploy", false)));

        var result = service.listEnabled();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("hello");
    }

    @Test
    @DisplayName("S012 AC-1: get() returns matching skill")
    void getReturnsMatching() {
        when(store.loadAll()).thenReturn(List.of(entry("hello", true)));

        var result = service.get("hello");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("hello");
    }

    @Test
    @DisplayName("S012 AC-1: get() returns empty for unknown skill")
    void getReturnsEmptyForUnknown() {
        when(store.loadAll()).thenReturn(List.of(entry("hello", true)));

        var result = service.get("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("S012 AC-3: disable() calls saveState with updated map")
    void disableCallsSaveState() {
        when(store.loadAll()).thenReturn(List.of(
                entry("hello", true),
                entry("deploy", true)));

        service.disable("hello");

        verify(store).saveState(argThat(map ->
                map.get("hello") == Boolean.FALSE && map.get("deploy") == Boolean.TRUE));
    }

    @Test
    @DisplayName("S012 AC-3: enable() calls saveState with updated map")
    void enableCallsSaveState() {
        when(store.loadAll()).thenReturn(List.of(entry("hello", false)));

        service.enable("hello");

        verify(store).saveState(argThat(map ->
                map.get("hello") == Boolean.TRUE));
    }

    @Test
    @DisplayName("S012: disable unknown skill throws IllegalArgumentException")
    void disableUnknownThrows() {
        when(store.loadAll()).thenReturn(List.of(entry("hello", true)));

        assertThatThrownBy(() -> service.disable("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
