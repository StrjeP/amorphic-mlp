package dev.amorphic.governor.service;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** No demo app is running in the test JVM, so the governor can't see the inventory and says so. */
@SpringBootTest(properties = "amorphic.app.url=http://127.0.0.1:9")
@AutoConfigureMockMvc
class GovernorServiceTest {

    @Autowired
    MockMvc mvc;

    @Test
    void answersHealth() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void cannotProposeWithoutTheInventory() throws Exception {
        mvc.perform(post("/governor/proposals"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.reason", containsString("inventory unavailable: inventory at http://127.0.0.1:9")));
    }

    @Test
    void unknownProposalsCannotBeApproved() throws Exception {
        mvc.perform(get("/governor/proposals/g1-x")).andExpect(status().isNotFound());
        mvc.perform(post("/governor/proposals/g1-x/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("rejected"));
    }
}
