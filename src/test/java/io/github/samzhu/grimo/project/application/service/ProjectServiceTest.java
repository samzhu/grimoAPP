package io.github.samzhu.grimo.project.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.samzhu.grimo.project.application.port.out.ProjectPort;
import io.github.samzhu.grimo.project.domain.DuplicateProjectNameException;
import io.github.samzhu.grimo.project.domain.Project;
import io.github.samzhu.grimo.project.domain.ProjectNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private final ProjectPort projectPort = mock(ProjectPort.class);
    private final ProjectService service = new ProjectService(projectPort);

    // ── create ──

    @Test
    @DisplayName("[S026] AC-3: create project with unique name succeeds")
    void createHappyPath() {
        // Given
        when(projectPort.findByName("myapp")).thenReturn(Optional.empty());

        // When
        Project result = service.create("myapp", "/home/user/myapp", "A demo project");

        // Then
        assertThat(result.name()).isEqualTo("myapp");
        assertThat(result.workDir()).isEqualTo("/home/user/myapp");
        assertThat(result.description()).isEqualTo("A demo project");
        assertThat(result.id()).isNotBlank();
        verify(projectPort).save(any(Project.class));
    }

    @Test
    @DisplayName("[S026] AC-3: create project with duplicate name throws DuplicateProjectNameException")
    void createDuplicateNameThrows() {
        // Given
        var existing = project("p1", "myapp");
        when(projectPort.findByName("myapp")).thenReturn(Optional.of(existing));

        // When / Then
        assertThatThrownBy(() -> service.create("myapp", "/tmp", null))
                .isInstanceOf(DuplicateProjectNameException.class)
                .hasMessageContaining("myapp");
        verify(projectPort, never()).save(any());
    }

    // ── listAll ──

    @Test
    @DisplayName("[S026] AC-3: listAll returns all projects")
    void listAll() {
        // Given
        var p1 = project("p1", "alpha");
        var p2 = project("p2", "beta");
        when(projectPort.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<Project> result = service.listAll();

        // Then
        assertThat(result).containsExactly(p1, p2);
    }

    // ── findById ──

    @Test
    @DisplayName("[S026] AC-3: findById returns project when exists")
    void findByIdExists() {
        // Given
        var p = project("p1", "myapp");
        when(projectPort.findById("p1")).thenReturn(Optional.of(p));

        // When
        Optional<Project> result = service.findById("p1");

        // Then
        assertThat(result).isPresent().contains(p);
    }

    @Test
    @DisplayName("[S026] AC-3: findById returns empty when not found")
    void findByIdNotFound() {
        // Given
        when(projectPort.findById("missing")).thenReturn(Optional.empty());

        // When
        Optional<Project> result = service.findById("missing");

        // Then
        assertThat(result).isEmpty();
    }

    // ── update ──

    @Test
    @DisplayName("[S026] AC-3: update applies patch semantics — null fields keep existing values")
    void updatePatchSemantics() {
        // Given
        var existing = new Project("p1", "oldname", "/old", "old desc",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(projectPort.findById("p1")).thenReturn(Optional.of(existing));

        // When — only update description, keep name and workDir
        Project result = service.update("p1", null, null, "new desc");

        // Then
        assertThat(result.name()).isEqualTo("oldname");
        assertThat(result.workDir()).isEqualTo("/old");
        assertThat(result.description()).isEqualTo("new desc");
        verify(projectPort).save(any(Project.class));
    }

    @Test
    @DisplayName("[S026] AC-3: update with new unique name succeeds")
    void updateRenameSameNameNoop() {
        // Given
        var existing = project("p1", "alpha");
        when(projectPort.findById("p1")).thenReturn(Optional.of(existing));
        when(projectPort.findByName("beta")).thenReturn(Optional.empty());

        // When
        Project result = service.update("p1", "beta", null, null);

        // Then
        assertThat(result.name()).isEqualTo("beta");
        verify(projectPort).save(any(Project.class));
    }

    @Test
    @DisplayName("[S026] AC-3: update rename to duplicate name throws DuplicateProjectNameException")
    void updateRenameGuard() {
        // Given
        var existing = project("p1", "alpha");
        var other = project("p2", "beta");
        when(projectPort.findById("p1")).thenReturn(Optional.of(existing));
        when(projectPort.findByName("beta")).thenReturn(Optional.of(other));

        // When / Then
        assertThatThrownBy(() -> service.update("p1", "beta", null, null))
                .isInstanceOf(DuplicateProjectNameException.class)
                .hasMessageContaining("beta");
        verify(projectPort, never()).save(any());
    }

    @Test
    @DisplayName("[S026] AC-3: update with same name does not trigger duplicate check")
    void updateKeepSameNameNoDuplicateCheck() {
        // Given
        var existing = project("p1", "alpha");
        when(projectPort.findById("p1")).thenReturn(Optional.of(existing));

        // When — name is same as existing, should not trigger findByName
        Project result = service.update("p1", "alpha", "/new-path", null);

        // Then
        assertThat(result.name()).isEqualTo("alpha");
        assertThat(result.workDir()).isEqualTo("/new-path");
        verify(projectPort, never()).findByName(any());
        verify(projectPort).save(any(Project.class));
    }

    @Test
    @DisplayName("[S026] AC-3: update non-existent project throws ProjectNotFoundException")
    void updateNotFoundThrows() {
        // Given
        when(projectPort.findById("missing")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.update("missing", "x", null, null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ── delete ──

    @Test
    @DisplayName("[S026] AC-3: delete existing project succeeds")
    void deleteHappyPath() {
        // Given
        var existing = project("p1", "alpha");
        when(projectPort.findById("p1")).thenReturn(Optional.of(existing));

        // When
        service.delete("p1");

        // Then
        verify(projectPort).deleteById("p1");
    }

    @Test
    @DisplayName("[S026] AC-3: delete non-existent project throws ProjectNotFoundException")
    void deleteNotFoundThrows() {
        // Given
        when(projectPort.findById("missing")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ── helper ──

    private static Project project(String id, String name) {
        var now = Instant.now();
        return new Project(id, name, "/tmp/" + name, null, now, now);
    }
}
