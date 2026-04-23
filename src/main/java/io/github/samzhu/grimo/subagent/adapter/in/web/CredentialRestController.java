package io.github.samzhu.grimo.subagent.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.samzhu.grimo.subagent.application.port.in.CredentialUseCase;
import io.github.samzhu.grimo.subagent.application.port.out.SettingPort;
import io.github.samzhu.grimo.subagent.domain.Credential;

/**
 * REST API for credential pool management (S030).
 *
 * <ul>
 *   <li>POST   /api/credentials — create credential</li>
 *   <li>GET    /api/credentials — list all (masked secrets)</li>
 *   <li>DELETE /api/credentials/{id} — delete credential</li>
 *   <li>PUT    /api/credentials/{id}/sort-order — update sort order</li>
 *   <li>PUT    /api/settings/credential-strategy — update strategy</li>
 *   <li>GET    /api/settings/credential-strategy — get strategy</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
class CredentialRestController {

    private final CredentialUseCase credentialUseCase;
    private final SettingPort settingPort;

    CredentialRestController(CredentialUseCase credentialUseCase, SettingPort settingPort) {
        this.credentialUseCase = credentialUseCase;
        this.settingPort = settingPort;
    }

    // --- Request/Response records ---

    record CreateCredentialRequest(String label, String provider, String credentialType,
                                   String secretValue, @Nullable Instant expiresAt) {}

    record CredentialResponse(String id, String label, String provider,
                              String credentialType, String maskedSecret,
                              int sortOrder, @Nullable Instant expiresAt,
                              Instant createdAt) {
        static CredentialResponse from(Credential c) {
            return new CredentialResponse(c.id(), c.label(), c.provider(),
                    c.credentialType(), c.maskedSecret(), c.sortOrder(),
                    c.expiresAt(), c.createdAt());
        }
    }

    record SortOrderRequest(int sortOrder) {}

    record SettingRequest(String value) {}

    record SettingResponse(String value) {}

    // --- Credential CRUD ---

    @PostMapping("/credentials")
    ResponseEntity<CredentialResponse> create(@RequestBody CreateCredentialRequest req) {
        var credential = credentialUseCase.create(
                req.label(), req.provider(), req.credentialType(),
                req.secretValue(), req.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CredentialResponse.from(credential));
    }

    @GetMapping("/credentials")
    List<CredentialResponse> listAll() {
        return credentialUseCase.listAll().stream()
                .map(CredentialResponse::from)
                .toList();
    }

    @DeleteMapping("/credentials/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        credentialUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/credentials/{id}/sort-order")
    ResponseEntity<Void> updateSortOrder(@PathVariable String id,
                                          @RequestBody SortOrderRequest req) {
        credentialUseCase.updateSortOrder(id, req.sortOrder());
        return ResponseEntity.ok().build();
    }

    // --- Strategy setting ---

    @PutMapping("/settings/credential-strategy")
    ResponseEntity<Void> updateStrategy(@RequestBody SettingRequest req) {
        settingPort.set("credential-strategy", req.value());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/settings/credential-strategy")
    SettingResponse getStrategy() {
        var value = settingPort.get("credential-strategy").orElse("PRIORITY");
        return new SettingResponse(value);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
