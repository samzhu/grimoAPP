package io.github.samzhu.grimo.project;

/**
 * One child directory shown in the Project Creation Page folder picker.
 *
 * @param name display name of the directory
 * @param path absolute local path submitted when selected
 * @see LocalDirectoryResponse
 */
public record LocalDirectoryEntryResponse(String name, String path) {
}
