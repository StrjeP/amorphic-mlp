package dev.amorphic.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import dev.amorphic.governor.Governor;
import dev.amorphic.governor.GovernorHttp;
import dev.amorphic.inventory.EvidenceView;
import dev.amorphic.inventory.InventorySnapshot;
import dev.amorphic.inventory.Json;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The inventory and governor go where the address table says, with no change at the call sites
 * (/inventory and /governor/...). A stub HTTP server stands in for their remote services.
 */
@SpringBootTest(properties = {
        "amorphic.addresses=../amorphic-demo-env/addresses/stage-4.yaml",
        "amorphic.inventory.min-evidence=0"})
@AutoConfigureMockMvc
@DirtiesContext
class ToolingRoutingTest {

    static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    @Autowired
    MockMvc mvc;

    HttpServer stub;
    String stubUrl;

    @BeforeEach
    void startStub() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        InventorySnapshot remote = new InventorySnapshot(Instant.parse("2026-11-10T12:00:00Z"), 7, "remote stub",
                true, List.of(), 100, List.of(), new EvidenceView(7, List.of(), List.of()), List.of(), List.of());
        stub.createContext("/inventory", ex -> reply(ex, 200, Json.MAPPER.writeValueAsString(remote)));
        stub.createContext("/governor/proposals", ex -> reply(ex, 200, Json.MAPPER.writeValueAsString(
                GovernorHttp.propose(new Governor.NoChange("answered by the remote governor")).body())));
        stub.start();
        stubUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    String table(String inventory, String governor) throws Exception {
        return Files.readString(Path.of("../amorphic-demo-env/addresses/stage-4.yaml"))
                + entry("inventory", inventory) + entry("governor", governor);
    }

    static String entry(String name, String binding) {
        return "  " + name + ":\n    champion:\n      address: pkg:amorphic/" + name + "@0.1?lang=java\n      binding: "
                + binding + "\n";
    }

    void apply(String yaml) throws Exception {
        mvc.perform(post("/admin/address-table").contentType(YAML).content(yaml)).andExpect(status().isOk());
    }

    @Test
    void remoteBindingsSendTheSameCallsToTheServices() throws Exception {
        String remote = "{ type: remote, url: " + stubUrl + " }";
        apply(table(remote, remote));
        mvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableSource").value("remote stub"))
                .andExpect(jsonPath("$.generation").value(7));
        mvc.perform(post("/governor/proposals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("no-change"))
                .andExpect(jsonPath("$.reason").value("answered by the remote governor"));
    }

    @Test
    void localBindingsRunInProcessAndTheCatalogueShowsThemRouted() throws Exception {
        apply(table("{ type: local }", "{ type: local }"));
        mvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.placements[?(@.capability == 'inventory')].health.status", hasItem("IN_PROCESS")))
                .andExpect(jsonPath("$.catalogue[?(@.capability == 'governor')].routedAs[0]", hasItem("champion")))
                .andExpect(jsonPath("$.problems.length()").value(0));
        mvc.perform(post("/governor/proposals"))
                .andExpect(jsonPath("$.status").value("no-change"))
                .andExpect(jsonPath("$.reason", containsString("folded in")));
    }

    @Test
    void anUnreachableRemoteInventoryIsReportedNotHidden() throws Exception {
        stub.stop(0);
        apply(table("{ type: remote, url: " + stubUrl + " }", "{ type: local }"));
        mvc.perform(get("/inventory"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", containsString("inventory at " + stubUrl)));
        // The local governor asks the routed inventory, so it can't see the system either.
        mvc.perform(post("/governor/proposals"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason", containsString("inventory unavailable")));
    }

    @Test
    void aToolingEntryThatCannotBeHonouredIsRefused() throws Exception {
        mvc.perform(post("/admin/address-table").contentType(YAML).content(table("{ type: remote }", "{ type: local }")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("remote binding for the inventory has no url"));
        mvc.perform(post("/admin/address-table").contentType(YAML).content(table("{ type: local }", "{ type: grpc }")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("governor needs a binding of type local or remote")));
    }
}
