package tech.dmj.maintain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Owns the Rust child process for this desktop session when one is not already running. */
public final class EngineSupervisor implements AutoCloseable {
    private final EngineIpcClient client = new EngineIpcClient();
    private Process child;

    public EngineIpcClient.EngineStatus startAndPing() throws IOException, InterruptedException {
        // Reuse an already-running local engine rather than creating a second one.
        try {
            return client.pingWithRetry(Duration.ofMillis(500));
        } catch (IOException unavailable) {
            // No responding local engine; continue with the bundled/development binary.
        }

        Path binary = locateEngineBinary();
        if (!Files.isRegularFile(binary)) {
            throw new IOException("Rust engine binary not found at " + binary
                    + ". Build it with: cargo build --package maintain-engine");
        }

        ProcessBuilder builder = new ProcessBuilder(binary.toAbsolutePath().toString());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        child = builder.start();
        return client.pingWithRetry(Duration.ofSeconds(12));
    }

    private static Path locateEngineBinary() {
        String configured = System.getProperty("maintain.engine.path");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }

        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repositoryRoot = workingDirectory;
        if (workingDirectory.endsWith(Path.of("apps", "desktop"))) {
            repositoryRoot = workingDirectory.getParent().getParent();
        }
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "maintain-engine.exe" : "maintain-engine";
        return repositoryRoot.resolve("target").resolve("debug").resolve(executable);
    }

    @Override
    public void close() {
        if (child == null || !child.isAlive()) {
            return;
        }
        child.destroy();
        try {
            if (!child.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                child.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            child.destroyForcibly();
        }
    }
}
