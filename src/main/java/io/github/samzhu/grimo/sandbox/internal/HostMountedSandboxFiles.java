package io.github.samzhu.grimo.sandbox.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.springaicommunity.sandbox.FileEntry;
import org.springaicommunity.sandbox.FileSpec;
import org.springaicommunity.sandbox.FileType;
import org.springaicommunity.sandbox.Sandbox;
import org.springaicommunity.sandbox.SandboxException;
import org.springaicommunity.sandbox.SandboxFiles;

/**
 * {@link SandboxFiles} 實作，直接在主機端以 {@code java.nio.file} 操作
 * bind-mounted 目錄。
 *
 * <p>設計說明：bind-mount 雙向同步，主機端檔案操作比繞道
 * {@code execInContainer} 更可靠且效能更好。見 spec S003 §2.1 D5。
 */
class HostMountedSandboxFiles implements SandboxFiles {

    private final Path hostMountPath;
    private final Sandbox sandbox;

    HostMountedSandboxFiles(Path hostMountPath, Sandbox sandbox) {
        this.hostMountPath = hostMountPath;
        this.sandbox = sandbox;
    }

    @Override
    public SandboxFiles create(String relativePath, String content) {
        try {
            var path = resolve(relativePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new SandboxException("Failed to create " + relativePath, e);
        }
        return this;
    }

    @Override
    public SandboxFiles createDirectory(String relativePath) {
        try {
            Files.createDirectories(resolve(relativePath));
        } catch (IOException e) {
            throw new SandboxException("Failed to create directory " + relativePath, e);
        }
        return this;
    }

    @Override
    public SandboxFiles setup(List<FileSpec> files) {
        for (var file : files) {
            create(file.path(), file.content());
        }
        return this;
    }

    @Override
    public String read(String relativePath) {
        try {
            return Files.readString(resolve(relativePath));
        } catch (IOException e) {
            throw new SandboxException("Failed to read " + relativePath, e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    @Override
    public List<FileEntry> list(String relativePath) {
        return list(relativePath, 1);
    }

    @Override
    public List<FileEntry> list(String relativePath, int maxDepth) {
        try (var stream = Files.walk(resolve(relativePath), maxDepth)) {
            return stream
                    .filter(p -> !p.equals(resolve(relativePath)))
                    .map(this::toFileEntry)
                    .toList();
        } catch (IOException e) {
            throw new SandboxException("Failed to list " + relativePath, e);
        }
    }

    @Override
    public SandboxFiles delete(String relativePath) {
        return delete(relativePath, false);
    }

    @Override
    public SandboxFiles delete(String relativePath, boolean recursive) {
        try {
            var path = resolve(relativePath);
            if (recursive) {
                try (var walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new SandboxException("Delete failed: " + p, e);
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new SandboxException("Failed to delete " + relativePath, e);
        }
        return this;
    }

    @Override
    public Sandbox and() {
        return sandbox;
    }

    private Path resolve(String relativePath) {
        return hostMountPath.resolve(relativePath);
    }

    private FileEntry toFileEntry(Path path) {
        try {
            return new FileEntry(
                    path.getFileName().toString(),
                    Files.isDirectory(path) ? FileType.DIRECTORY : FileType.FILE,
                    hostMountPath.relativize(path).toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (IOException e) {
            throw new SandboxException("Failed to stat " + path, e);
        }
    }
}
