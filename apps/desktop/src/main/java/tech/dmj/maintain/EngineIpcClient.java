package tech.dmj.maintain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Small synchronous request client for the local engine bootstrap protocol. */
public final class EngineIpcClient {
    private static final int PROTOCOL_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    public record EngineStatus(String engine, int protocolVersion) {}

    public EngineStatus pingWithRetry(Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException lastError = null;
        do {
            try {
                return pingOnce();
            } catch (IOException error) {
                lastError = error;
                Thread.sleep(150);
            }
        } while (System.nanoTime() < deadline);

        throw new IOException("The local engine did not accept an IPC connection before timeout.", lastError);
    }

    private EngineStatus pingOnce() throws IOException {
        try (ByteChannel channel = openChannel();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     Channels.newOutputStream(channel), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     Channels.newInputStream(channel), StandardCharsets.UTF_8))) {

            Map<String, Object> request = Map.of(
                    "request_id", UUID.randomUUID().toString(),
                    "protocol_version", PROTOCOL_VERSION,
                    "message", Map.of("kind", "ping")
            );
            writer.write(JSON.writeValueAsString(request));
            writer.newLine();
            writer.flush();

            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                throw new IOException("The engine closed the IPC channel without a response.");
            }

            JsonNode envelope = JSON.readTree(line);
            JsonNode message = envelope.path("message");
            if (!"pong".equals(message.path("kind").asText())) {
                throw new IOException("Unexpected engine IPC response: " + line);
            }
            JsonNode payload = message.path("payload");
            return new EngineStatus(
                    payload.path("engine").asText("MAINTAIN AI Native Engine"),
                    payload.path("protocol_version").asInt(-1)
            );
        }
    }

    private ByteChannel openChannel() throws IOException {
        if (isWindows()) {
            Path pipe = Path.of("\\\\.\\pipe\\maintain-ai-native");
            return Files.newByteChannel(pipe, StandardOpenOption.READ, StandardOpenOption.WRITE);
        }

        Path socket = localDataDirectory().resolve("engine.sock");
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            channel.connect(UnixDomainSocketAddress.of(socket));
            return channel;
        } catch (IOException error) {
            channel.close();
            throw error;
        }
    }

    private static Path localDataDirectory() {
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "DMJ Group", "MAINTAIN AI Native");
            }
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            if (xdg != null && !xdg.isBlank()) {
                return Path.of(xdg, "DMJ Group", "MAINTAIN AI Native");
            }
        }
        return Path.of(System.getProperty("user.home"), ".local", "share",
                "DMJ Group", "MAINTAIN AI Native");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
