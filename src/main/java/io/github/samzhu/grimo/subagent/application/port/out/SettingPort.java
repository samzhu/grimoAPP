package io.github.samzhu.grimo.subagent.application.port.out;

import java.util.Optional;

/**
 * Outbound port for key-value settings (S030).
 */
public interface SettingPort {

    Optional<String> get(String key);

    void set(String key, String value);
}
