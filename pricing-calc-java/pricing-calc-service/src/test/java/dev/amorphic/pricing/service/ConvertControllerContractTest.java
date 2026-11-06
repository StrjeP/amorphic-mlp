package dev.amorphic.pricing.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.amorphic.pricing.GoldenVectors;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConvertController.class)
class ConvertControllerContractTest {

    @Autowired
    MockMvc mvc;

    static List<GoldenVectors.Case> golden() {
        return GoldenVectors.load();
    }

    @ParameterizedTest
    @MethodSource("golden")
    void matchesGoldenVector(GoldenVectors.Case c) throws Exception {
        mvc.perform(get("/convert").param("amount", c.amount()).param("from", c.from()).param("to", c.to()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(c.expected()))
                .andExpect(jsonPath("$.currency").value(c.to()));
    }

    @Test
    void unsupportedPairIs400() throws Exception {
        mvc.perform(get("/convert").param("amount", "1.00").param("from", "USD").param("to", "JPY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void invalidAmountIs400() throws Exception {
        for (String bad : List.of("abc", "-1")) {
            mvc.perform(get("/convert").param("amount", bad).param("from", "GBP").param("to", "USD"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void health() throws Exception {
        mvc.perform(get("/health")).andExpect(jsonPath("$.status").value("ok"));
    }
}
