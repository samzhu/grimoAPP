package io.github.samzhu.grimo.project.adapter.out;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.project.application.port.out.ProjectPort;
import io.github.samzhu.grimo.project.domain.Project;

@Repository
class JdbcProjectAdapter implements ProjectPort {

    private final JdbcTemplate jdbc;

    JdbcProjectAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Project p) {
        jdbc.update("""
                MERGE INTO grimo_project (id, name, work_dir, description, created_at, updated_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?)""",
                p.id(), p.name(), p.workDir(), p.description(),
                Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()));
    }

    @Override
    public Optional<Project> findById(String id) {
        var results = jdbc.query(
                "SELECT * FROM grimo_project WHERE id = ?",
                this::mapRow, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<Project> findByName(String name) {
        var results = jdbc.query(
                "SELECT * FROM grimo_project WHERE name = ?",
                this::mapRow, name);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<Project> findAll() {
        return jdbc.query(
                "SELECT * FROM grimo_project ORDER BY created_at DESC",
                this::mapRow);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM grimo_project WHERE id = ?", id);
    }

    private Project mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Project(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("work_dir"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
