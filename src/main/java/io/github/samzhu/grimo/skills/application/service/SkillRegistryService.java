package io.github.samzhu.grimo.skills.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.application.port.out.SkillStorePort;
import io.github.samzhu.grimo.skills.domain.SkillEntry;
import org.springframework.stereotype.Service;

@Service
class SkillRegistryService implements SkillRegistryUseCase {

    private final SkillStorePort store;

    SkillRegistryService(SkillStorePort store) {
        this.store = store;
    }

    @Override
    public List<SkillEntry> list() {
        return store.loadAll();
    }

    @Override
    public List<SkillEntry> listEnabled() {
        return store.loadAll().stream().filter(SkillEntry::enabled).toList();
    }

    @Override
    public Optional<SkillEntry> get(String name) {
        return store.loadAll().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
    }

    @Override
    public void enable(String name) {
        updateState(name, true);
    }

    @Override
    public void disable(String name) {
        updateState(name, false);
    }

    private void updateState(String name, boolean enabled) {
        List<SkillEntry> all = store.loadAll();
        if (all.stream().noneMatch(e -> e.name().equals(name))) {
            throw new IllegalArgumentException("Skill not found: " + name);
        }
        Map<String, Boolean> states = new HashMap<>();
        all.forEach(e -> states.put(e.name(), e.enabled()));
        states.put(name, enabled);
        store.saveState(states);
    }
}
