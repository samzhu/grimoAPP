package io.github.samzhu.grimo.skills.adapter.in.web;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.samzhu.grimo.skills.application.port.in.SkillProjectionUseCase;
import io.github.samzhu.grimo.skills.application.port.in.SkillRegistryUseCase;
import io.github.samzhu.grimo.skills.domain.SkillEntry;

@RestController
@RequestMapping("/api/skills")
class SkillRestController {

    private final SkillRegistryUseCase registry;
    private final SkillProjectionUseCase projection;

    SkillRestController(SkillRegistryUseCase registry, SkillProjectionUseCase projection) {
        this.registry = registry;
        this.projection = projection;
    }

    record SkillDto(String name, boolean enabled, String description) {
        static SkillDto from(SkillEntry entry) {
            var fm = entry.skill().frontMatter();
            String desc = fm != null && fm.containsKey("description")
                    ? String.valueOf(fm.get("description")) : "";
            return new SkillDto(entry.name(), entry.enabled(), desc);
        }
    }

    record ProjectSkillsRequest(String workDir) {}

    @GetMapping
    List<SkillDto> list() {
        return registry.list().stream().map(SkillDto::from).toList();
    }

    @GetMapping("/{name}")
    ResponseEntity<SkillDto> get(@PathVariable String name) {
        return registry.get(name)
                .map(e -> ResponseEntity.ok(SkillDto.from(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{name}/enable")
    ResponseEntity<Void> enable(@PathVariable String name) {
        registry.enable(name);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{name}/disable")
    ResponseEntity<Void> disable(@PathVariable String name) {
        registry.disable(name);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/project")
    ResponseEntity<Void> projectSkills(@RequestBody ProjectSkillsRequest req) {
        projection.projectToWorkDir(Path.of(req.workDir()));
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
