package dev.amorphic.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Starts the app on the stage 0 table and changes it over HTTP, without a restart. */
@SpringBootTest(properties = "amorphic.addresses=../amorphic-demo-env/addresses/stage-0.yaml")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminControllerTest {

    static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    @Autowired
    MockMvc mvc;

    static String stage(String id) throws Exception {
        return Files.readString(Path.of("../amorphic-demo-env/addresses/stage-" + id + ".yaml"));
    }

    @Test
    void showsTheStartupTable() throws Exception {
        mvc.perform(get("/admin/address-table"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation").value(1))
                .andExpect(jsonPath("$.table.capabilities.pricing-calc.champion.address")
                        .value("pkg:amorphic/pricing-calc@1.0?lang=python"));
    }

    @Test
    void appliesAStageTableLive() throws Exception {
        mvc.perform(post("/admin/address-table").contentType(YAML).header("X-Table-Source", "stage-1.yaml")
                        .content(stage("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation").value(2))
                .andExpect(jsonPath("$.source").value("stage-1.yaml"))
                .andExpect(jsonPath("$.routing").value(org.hamcrest.Matchers.containsString(
                        "shadow=pkg:amorphic/pricing-calc@2.0?lang=java")));
        mvc.perform(get("/evidence")).andExpect(jsonPath("$.generation").value(2));
    }

    @Test
    void rejectsATableItCannotHonourAndKeepsTheCurrentOne() throws Exception {
        String wrongVersion = stage("4").replace("@2.1", "@9.9");
        mvc.perform(post("/admin/address-table").contentType(YAML).content(wrongVersion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("version 9.9")))
                .andExpect(jsonPath("$.generation").value(1));
        mvc.perform(get("/admin/address-table")).andExpect(jsonPath("$.generation").value(1));
    }

    @Test
    void rejectsMalformedYaml() throws Exception {
        mvc.perform(post("/admin/address-table").contentType(YAML).content("capabilities: ["))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
