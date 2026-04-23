package io.github.samzhu.grimo.subagent.adapter.out;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.samzhu.grimo.subagent.application.port.out.SettingPort;

/**
 * JDBC adapter for grimo_setting table (S030).
 */
@Repository
class JdbcSettingAdapter implements SettingPort {

    private final JdbcTemplate jdbc;

    JdbcSettingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> get(String key) {
        var list = jdbc.query(
                "SELECT setting_value FROM grimo_setting WHERE setting_key = ?",
                (rs, rowNum) -> rs.getString("setting_value"), key);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public void set(String key, String value) {
        jdbc.update("""
                MERGE INTO grimo_setting (setting_key, setting_value)
                KEY (setting_key)
                VALUES (?, ?)""",
                key, value);
    }
}
