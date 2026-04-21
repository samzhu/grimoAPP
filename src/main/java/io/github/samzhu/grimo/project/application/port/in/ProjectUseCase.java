package io.github.samzhu.grimo.project.application.port.in;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.samzhu.grimo.project.domain.Project;

/**
 * Inbound port for project management (S018).
 */
public interface ProjectUseCase {

    Project create(String name, String workDir, @Nullable String description);

    List<Project> listAll();

    Optional<Project> findById(String id);

    Project update(String id, @Nullable String name,
                   @Nullable String workDir, @Nullable String description);

    void delete(String id);
}
