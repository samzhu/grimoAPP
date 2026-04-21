package io.github.samzhu.grimo.project.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.core.domain.NanoIds;
import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.project.application.port.out.ProjectPort;
import io.github.samzhu.grimo.project.domain.DuplicateProjectNameException;
import io.github.samzhu.grimo.project.domain.Project;
import io.github.samzhu.grimo.project.domain.ProjectNotFoundException;

@Service
class ProjectService implements ProjectUseCase {

    private final ProjectPort projectPort;

    ProjectService(ProjectPort projectPort) {
        this.projectPort = projectPort;
    }

    @Override
    public Project create(String name, String workDir, @Nullable String description) {
        if (projectPort.findByName(name).isPresent()) {
            throw new DuplicateProjectNameException(name);
        }
        var now = Instant.now();
        var project = new Project(NanoIds.compact(), name, workDir, description, now, now);
        projectPort.save(project);
        return project;
    }

    @Override
    public List<Project> listAll() {
        return projectPort.findAll();
    }

    @Override
    public Optional<Project> findById(String id) {
        return projectPort.findById(id);
    }

    @Override
    public Project update(String id, @Nullable String name,
                          @Nullable String workDir, @Nullable String description) {
        var existing = projectPort.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));

        if (name != null && !name.equals(existing.name())) {
            if (projectPort.findByName(name).isPresent()) {
                throw new DuplicateProjectNameException(name);
            }
        }

        var updated = new Project(
                existing.id(),
                name != null ? name : existing.name(),
                workDir != null ? workDir : existing.workDir(),
                description != null ? description : existing.description(),
                existing.createdAt(),
                Instant.now());
        projectPort.save(updated);
        return updated;
    }

    @Override
    public void delete(String id) {
        projectPort.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
        projectPort.deleteById(id);
    }
}
