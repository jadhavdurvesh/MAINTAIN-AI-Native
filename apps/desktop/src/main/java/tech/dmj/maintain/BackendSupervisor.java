package tech.dmj.maintain;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Runs the migrated FastAPI service as a local-only sidecar backed by SQLite. */
public final class BackendSupervisor implements AutoCloseable {
    private Process process;
    private Path logFile;
    private int port;

    public String startAndWait() throws IOException, InterruptedException {
        Path root = locateRepositoryRoot();
        Path backendDir = root.resolve("backend");
        Path entrypoint = backendDir.resolve("run_server.py");
        if (!Files.isRegularFile(entrypoint)) throw new IOException("FastAPI entrypoint not found: " + entrypoint);

        port = reservePort();
        Path dataDir = applicationDataDirectory();
        Files.createDirectories(dataDir.resolve("logs"));
        logFile = dataDir.resolve("logs/backend.log");
        Path database = dataDir.resolve("maintain-ai.sqlite3");

        ProcessBuilder builder = new ProcessBuilder(configuredPython(backendDir).toString(), entrypoint.toString());
        builder.directory(backendDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        var env = builder.environment();
        env.put("PORT", Integer.toString(port));
        env.put("DATABASE_URL", "sqlite:///" + database.toAbsolutePath().normalize().toString().replace(FileSeparator.BACKSLASH, '/'));
        env.put("REQUIRE_AUTH", "false");
        env.put("SEED_DEMO_DATA", "false");
        env.put("VERCEL", "");
        env.put("CORS_ORIGINS", "");
        env.put("PYTHONPATH", backendDir.toString());
        env.put("PYTHONUNBUFFERED", "1");
        env.put("SUPABASE_URL", "");
        env.put("SUPABASE_PUBLISHABLE_KEY", "");
        env.put("SUPABASE_SECRET_KEY", "");
        env.put("SUPABASE_JWT_SECRET", "");

        process = builder.start();
        waitUntilReady(Duration.ofSeconds(60));
        return "http://127.0.0.1:" + port;
    }

    private void waitUntilReady(Duration timeout) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        URI health = URI.create("http://127.0.0.1:" + port + "/api/auth/status");
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException last = null;
        while (System.nanoTime() < deadline) {
            if (process == null || !process.isAlive()) throw new IOException("FastAPI stopped during startup. Log: " + logFile);
            try {
                var request = HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(2)).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return;
                last = new IOException("Health check returned HTTP " + response.statusCode());
            } catch (IOException error) {
                last = error;
            }
            Thread.sleep(300);
        }
        throw new IOException("FastAPI did not become ready. Log: " + logFile, last);
    }

    private static Path configuredPython(Path backendDir) throws IOException {
        String configured = System.getProperty("maintain.backend.python");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isRegularFile(path)) return path;
            throw new IOException("Configured Python executable does not exist: " + path);
        }
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path venv = backendDir.resolve(windows ? ".venv/Scripts/python.exe" : ".venv/bin/python");
        if (Files.isRegularFile(venv)) return venv;
        String fromEnv = System.getenv("PYTHON");
        return fromEnv == null || fromEnv.isBlank() ? Path.of(windows ? "python.exe" : "python3") : Path.of(fromEnv);
    }

    private static int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    static Path applicationDataDirectory() {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        if (windows) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) return Path.of(local, "DMJ Group", "MAINTAIN AI Native");
            return Path.of(System.getProperty("user.home"), "AppData", "Local", "DMJ Group", "MAINTAIN AI Native");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) return Path.of(xdg, "DMJ Group", "MAINTAIN AI Native");
        return Path.of(System.getProperty("user.home"), ".local", "share", "DMJ Group", "MAINTAIN AI Native");
    }

    private static Path locateRepositoryRoot() throws IOException {
        String configured = System.getProperty("maintain.repo.root");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath();
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("backend/app")) && Files.isDirectory(candidate.resolve("frontend/src"))) return candidate;
        }
        throw new IOException("Cannot locate repository root from " + current + ". Set -Dmaintain.repo.root=/path/to/MAINTAIN-AI-Native");
    }

    public Path getLogFile() { return logFile; }
    @Override public void close() {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static final class FileSeparator {
        private static final char BACKSLASH = 92;
    }
}
