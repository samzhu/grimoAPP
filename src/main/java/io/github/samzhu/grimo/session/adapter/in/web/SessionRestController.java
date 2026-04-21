package io.github.samzhu.grimo.session.adapter.in.web;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.samzhu.grimo.session.application.port.in.SessionHistoryUseCase;
import io.github.samzhu.grimo.session.domain.SessionEvent;
import io.github.samzhu.grimo.session.domain.SessionProjection;

@RestController
@RequestMapping("/api/sessions")
class SessionRestController {

    private final SessionHistoryUseCase sessionHistory;

    SessionRestController(SessionHistoryUseCase sessionHistory) {
        this.sessionHistory = sessionHistory;
    }

    @GetMapping
    List<SessionProjection> list(
            @RequestParam(required = false) @Nullable String sessionType,
            @RequestParam(required = false) @Nullable String projectId) {
        if (sessionType != null) {
            return sessionHistory.findBySessionType(sessionType);
        }
        if (projectId != null) {
            return sessionHistory.findByProjectId(projectId);
        }
        return sessionHistory.listAll();
    }

    @GetMapping("/{id}")
    ResponseEntity<SessionProjection> findById(@PathVariable String id) {
        return sessionHistory.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/events")
    ResponseEntity<?> getEvents(@PathVariable String id) {
        if (sessionHistory.findById(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found: " + id));
        }
        List<SessionEvent> events = sessionHistory.getEvents(id);
        return ResponseEntity.ok(events);
    }
}
