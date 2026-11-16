package tech.dmj.maintain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Synchronous request client for the local engine bootstrap protocol. */
public final class EngineIpcClient {
    private static final int PROTOCOL_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    public record EngineStatus(String engine, int protocolVersion) {}
    public record MachineDto(
            String id, String name, String assetTag, String machineType,
            String location, String status, String createdAt) {}

    public EngineStatus pingWithRetry(Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException lastError = null;
        do {
            try {
                JsonNode response = send("ping", null);
                requireKind(response, "pong");
                JsonNode payload = response.path("payload");
                return new EngineStatus(
                        payload.path("engine").asText("MAINTAIN AI Native Engine"),
                        payload.path("protocol_version").asInt(-1));
            } catch (IOException error) {
                lastError = error;
                Thread.sleep(150);
            }
        } while (System.nanoTime() < deadline);
        throw new IOException("The local engine did not accept an IPC connection before timeout.", lastError);
    }

    public List<MachineDto> listMachines() throws IOException {
        JsonNode response = send("list_machines", null);
        requireKind(response, "machines_listed");
        List<MachineDto> machines = new ArrayList<>();
        for (JsonNode node : response.path("payload").path("machines")) {
            machines.add(readMachine(node));
        }
        return machines;
    }

    public MachineDto createMachine(String name, String assetTag, String machineType, String location)
            throws IOException {
        Map<String, Object> payload = Map.of(
                "name", name,
                "asset_tag", assetTag,
                "machine_type", machineType,
                "location", location == null || location.isBlank() ? "" : location
        );
        JsonNode response = send("create_machine", payload);
        requireKind(response, "machine_created");
        return readMachine(response.path("payload").path("machine"));
    }

    private static MachineDto readMachine(JsonNode node) {
        return new MachineDto(
                node.path("id").asText(),
                node.path("name").asText(),
                node.path("asset_tag").asText(),
                node.path("machine_type").asText(),
                node.path("location").isNull() ? "" : node.path("location").asText(""),
                node.path("status").asText("Offline"),
                node.path("created_at").asText()
        );
    }

    private static void requireKind(JsonNode response, String expected) throws IOException {
        JsonNode message = response.path("message");
        String kind = message.path("kind").asText();
        if ("error".equals(kind)) {
            throw new IOException(message.path("payload").path("message").asText("Engine request failed."));
        }
        if (!expected.equals(kind)) {
            throw new IOException("Unexpected engine IPC response: " + message);
        }
    }

    private JsonNode send(String kind, Map<String, Object> payload) throws IOException {
        try (ByteChannel channel = openChannel();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     Channels.newOutputStream(channel), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     Channels.newInputStream(channel), StandardCharsets.UTF_8))) {
            Map<String, Object> message = payload == null
                    ? Map.of("kind", kind)
                    : Map.of("kind", kind, "payload", payload);
            Map<String, Object> envelope = Map.of(
                    "request_id", UUID.randomUUID().toString(),
                    "protocol_version", PROTOCOL_VERSION,
                    "message", message
            );
            writer.write(JSON.writeValueAsString(envelope));
            writer.newLine();
            writer.flush();

            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                throw new IOException("The engine closed the IPC channel without a response.");
            }
            JsonNode response = JSON.readTree(line);
            if (response.path("protocol_version").asInt(-1) != PROTOCOL_VERSION) {
                throw new IOException("The engine returned an incompatible IPC protocol version.");
            }
            return response;
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
        if (isWindows()) {
            return Path.of(System.getProperty("user.home"), "AppData", "Local",
                    "DMJ Group", "MAINTAIN AI Native");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share",
                "DMJ Group", "MAINTAIN AI Native");
    }
}
