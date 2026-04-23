/**
 * Task domain types — exposed as part of task::api so that
 * cross-module consumers of {@code TaskUseCase} can access
 * return types ({@code Task}, {@code TaskStatus}, etc.).
 */
@NamedInterface("api")
package io.github.samzhu.grimo.task.domain;

import org.springframework.modulith.NamedInterface;
