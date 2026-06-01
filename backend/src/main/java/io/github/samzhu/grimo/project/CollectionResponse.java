package io.github.samzhu.grimo.project;

import java.util.List;

/**
 * Envelope for complete, non-paged API collections.
 *
 * @param content items returned by the collection endpoint; empty when no item exists
 * @see ProjectController
 */
public record CollectionResponse<T>(List<T> content) {
}
