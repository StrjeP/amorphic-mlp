package dev.amorphic.pricing.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** With no declared identity, the service says so rather than make one up. */
@WebMvcTest(MetaController.class)
@Import(Self.class)
class MetaWithoutIdentityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void metaIsUnavailableWithoutADeclaredIdentity() throws Exception {
        mvc.perform(get("/meta")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AMORPHIC_INSTANCE")));
    }
}
