package io.github.samzhu.grimo.subagent.adapter.out;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.subagent.application.port.out.CredentialPort;
import io.github.samzhu.grimo.subagent.domain.Credential;

/**
 * JDBC adapter for grimo_credential table (S030).
 */
@Repository
class JdbcCredentialAdapter implements CredentialPort {

    private final JdbcTemplate jdbc;

    JdbcCredentialAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Credential c) {
        jdbc.update("""
                MERGE INTO grimo_credential (id, label, provider, credential_type,
                    secret_value, sort_order, expires_at, created_at)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                c.id(), c.label(), c.provider(), c.credentialType(),
                c.secretValue(), c.sortOrder(),
                c.expiresAt() != null ? Timestamp.from(c.expiresAt()) : null,
                Timestamp.from(c.createdAt()));
    }

    @Override
    public Optional<Credential> findById(String id) {
        var list = jdbc.query(
                "SELECT * FROM grimo_credential WHERE id = ?",
                this::mapRow, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Credential> findAll() {
        return jdbc.query(
                "SELECT * FROM grimo_credential ORDER BY sort_order ASC, created_at ASC",
                this::mapRow);
    }

    @Override
    public List<Credential> findByProviderOrderBySortOrder(String provider) {
        return jdbc.query(
                "SELECT * FROM grimo_credential WHERE provider = ? ORDER BY sort_order ASC",
                this::mapRow, provider);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM grimo_credential WHERE id = ?", id);
    }

    private Credential mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expiresTs = rs.getTimestamp("expires_at");
        return new Credential(
                rs.getString("id"),
                rs.getString("label"),
                rs.getString("provider"),
                rs.getString("credential_type"),
                rs.getString("secret_value"),
                rs.getInt("sort_order"),
                expiresTs != null ? expiresTs.toInstant() : null,
                rs.getTimestamp("created_at").toInstant());
    }
}
