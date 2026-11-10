package dev.amorphic.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

class GovernorControllerTest {

    /** Stage 0 in a test JVM: py-pricing can't answer /health, so the governor waits for the inventory. */
    @Nested
    @SpringBootTest(properties = "amorphic.addresses=../amorphic-demo-env/addresses/stage-0.yaml")
    @AutoConfigureMockMvc
    @DirtiesContext
    class NotReady {
        @Autowired
        MockMvc mvc;

        @Test
        void waitsAndSaysWhy() throws Exception {
            mvc.perform(post("/governor/proposals"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("waiting"))
                    .andExpect(jsonPath("$.reasons", hasItem(containsString("not answering /health"))));
        }

        @Test
        void unknownProposalsAreNotFoundAndCannotBeApproved() throws Exception {
            mvc.perform(get("/governor/proposals/g1-nothing")).andExpect(status().isNotFound());
            mvc.perform(post("/governor/proposals/g1-nothing/approve"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("rejected"));
        }
    }

    /** Stage 4 with core on the test classpath: folded in, so there is nothing left to propose. */
    @Nested
    @SpringBootTest(properties = {
            "amorphic.addresses=../amorphic-demo-env/addresses/stage-4.yaml",
            "amorphic.inventory.min-evidence=0"})
    @AutoConfigureMockMvc
    @DirtiesContext
    class FoldedIn {
        @Autowired
        MockMvc mvc;

        @Test
        void hasNothingFurtherToPropose() throws Exception {
            mvc.perform(post("/governor/proposals"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("no-change"))
                    .andExpect(jsonPath("$.reason", containsString("folded in")));
        }
    }
}
