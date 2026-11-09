package dev.amorphic.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

class InventoryControllerTest {

    /** Stage 0 in a test JVM: py-pricing isn't running, so it can't answer /health. */
    @Nested
    @SpringBootTest(properties = "amorphic.addresses=../amorphic-demo-env/addresses/stage-0.yaml")
    @AutoConfigureMockMvc
    @DirtiesContext
    class RemoteStage {
        @Autowired
        MockMvc mvc;

        @Test
        void reportsAnAddressThatDoesNotAnswerAndTooLittleEvidence() throws Exception {
            mvc.perform(get("/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ready").value(false))
                    .andExpect(jsonPath("$.placements[0].address").value("pkg:amorphic/pricing-calc@1.0?lang=python"))
                    .andExpect(jsonPath("$.placements[0].health.status").value("DOWN"))
                    .andExpect(jsonPath("$.notReadyBecause", hasItem(containsString("not answering /health"))))
                    .andExpect(jsonPath("$.notReadyBecause", hasItem(containsString("only 0 evidence records"))));
        }

        @Test
        void showsTheToolingInTheCatalogue() throws Exception {
            mvc.perform(get("/inventory"))
                    .andExpect(jsonPath("$.catalogue[?(@.capability == 'inventory')].runningAs",
                            hasItem("in-process in the demo app (this inventory)")))
                    .andExpect(jsonPath("$.catalogue[?(@.capability == 'catalogue')].runningAs",
                            hasItem(containsString("catalogue.yaml"))))
                    .andExpect(jsonPath("$.catalogue[?(@.capability == 'governor')].planned",
                            hasItem("governor phase 3")));
        }

        @Test
        void followsTheLiveTableAfterAHotReload() throws Exception {
            String stage1 = Files.readString(Path.of("../amorphic-demo-env/addresses/stage-1.yaml"));
            mvc.perform(post("/admin/address-table").contentType(MediaType.parseMediaType("application/yaml"))
                    .content(stage1)).andExpect(status().isOk());
            mvc.perform(get("/inventory"))
                    .andExpect(jsonPath("$.generation").value(2))
                    .andExpect(jsonPath("$.placements.length()").value(2))
                    .andExpect(jsonPath("$.problems", hasItem(containsString("withdrawn"))));
        }
    }

    /** Stage 4 with core on the test classpath: the champion is in-process, so nothing is probed. */
    @Nested
    @SpringBootTest(properties = {
            "amorphic.addresses=../amorphic-demo-env/addresses/stage-4.yaml",
            "amorphic.inventory.min-evidence=0"})
    @AutoConfigureMockMvc
    @DirtiesContext
    class LocalStage {
        @Autowired
        MockMvc mvc;

        @Test
        void readyWithAnInProcessChampion() throws Exception {
            mvc.perform(get("/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ready").value(true))
                    .andExpect(jsonPath("$.placements[0].health.status").value("IN_PROCESS"))
                    .andExpect(jsonPath("$.problems.length()").value(0));
        }
    }
}
