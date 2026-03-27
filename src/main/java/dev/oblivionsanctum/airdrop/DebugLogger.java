package dev.oblivionsanctum.airdrop;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class DebugLogger {
    private static final String SESSION_ID = "51b7f7";
    private static final String LOG_PATH = "debug-51b7f7.log";

    private DebugLogger() {}

    public static void log(String runId, String hypothesisId, String location, String message, Map<String, Object> data) {
        String id = "log_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"sessionId\":\"").append(escape(SESSION_ID)).append("\",");
        sb.append("\"id\":\"").append(escape(id)).append("\",");
        sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
        sb.append("\"location\":\"").append(escape(location)).append("\",");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"runId\":\"").append(escape(runId)).append("\",");
        sb.append("\"hypothesisId\":\"").append(escape(hypothesisId)).append("\",");
        sb.append("\"data\":").append(mapToJson(data)).append("}\n");

        try (FileWriter fw = new FileWriter(LOG_PATH, StandardCharsets.UTF_8, true)) {
            fw.write(sb.toString());
        } catch (IOException ignored) {
        }
    }

    private static String mapToJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object val = e.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(escape(String.valueOf(val))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String in) {
        return in.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
