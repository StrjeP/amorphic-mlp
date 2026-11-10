package dev.amorphic.inventory.service;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** No demo app is running in the test JVM, so the inventory can't read the live table and says so. */
@SpringBootTest(properties = "amorphic.app.url=http://127.0.0.1:9")
@AutoConfigureMockMvc
class InventoryServiceTest {

    @Autowired
    MockMvc mvc;

    @Test
    void answersHealth() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void reportsThatItCannotReachTheApp() throws Exception {
        mvc.perform(get("/inventory").param("timeoutMs", "1000"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", containsString("cannot read the live address table")))
                .andExpect(jsonPath("$.error", containsString("/admin/address-table")));
    }
}
