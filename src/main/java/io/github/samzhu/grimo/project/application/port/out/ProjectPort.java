package io.github.samzhu.grimo.project.application.port.out;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.project.domain.Project;

/**
 * Outbound port for project persistence.
 */
public interface ProjectPort {

    void save(Project project);

    Optional<Project> findById(String id);

    Optional<Project> findByName(String name);

    List<Project> findAll();

    void deleteById(String id);
}
