package io.github.samzhu.grimo.project;

import io.hypersistence.tsid.TSID;

import org.springframework.stereotype.Component;

/**
 * Generates short TSID resource identifiers for Grimo-owned rows.
 *
 * @see ProjectService
 */
@Component
public class ShortResourceIdGenerator {

	public String newId() {
		return TSID.Factory.getTsid().toString();
	}
}
