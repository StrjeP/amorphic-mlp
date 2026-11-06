package dev.amorphic.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads amorphic-demo-env/golden/vectors.json; Gradle passes its path as the golden.vectors property. */
public final class GoldenVectors {

    public record Case(String name, String amount, String from, String to, String expected) {
        @Override
        public String toString() {
            return amount + " " + from + "->" + to + " = " + expected + " (" + name + ")";
        }
    }

    private GoldenVectors() {}

    public static List<Case> load() {
        String path = System.getProperty("golden.vectors");
        if (path == null) {
            throw new IllegalStateException("system property golden.vectors is not set");
        }
        try {
            JsonNode root = new ObjectMapper().readTree(Path.of(path).toFile());
            List<Case> cases = new ArrayList<>();
            for (JsonNode c : root.get("cases")) {
                cases.add(new Case(c.get("name").asText(), c.get("amount").asText(), c.get("from").asText(),
                        c.get("to").asText(), c.get("expected").asText()));
            }
            if (cases.isEmpty()) {
                throw new IllegalStateException("golden vectors file has no cases: " + path);
            }
            return cases;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read golden vectors " + path, e);
        }
    }
}
