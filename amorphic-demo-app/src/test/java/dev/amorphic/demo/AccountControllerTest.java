package dev.amorphic.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The account report and the quote, both asked of the composer, on the stage 4 table: Java 2.1 folded into the app
 * (the tests have pricing-calc-core on the classpath), so no service is needed.
 */
@SpringBootTest(properties = "amorphic.addresses=../amorphic-demo-env/addresses/stage-4.yaml")
@AutoConfigureMockMvc
class AccountControllerTest {

    static final String LOCAL_JAVA = "pkg:amorphic/pricing-calc@2.1?lang=java";

    @Autowired
    MockMvc mvc;

    @Test
    void valuesTheSampleAccount() throws Exception {
        mvc.perform(get("/accounts/A-1001/value").header("X-Request-Id", "report-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("2216.22"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.positions[0].value").value("916.22"))
                .andExpect(jsonPath("$.positions[0].convertedBy").value(LOCAL_JAVA))
                .andExpect(jsonPath("$.positions[1].value").value("1050.00"))
                .andExpect(jsonPath("$.positions[2].value").value("250.00"))
                .andExpect(jsonPath("$.positions[2].convertedBy").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.outcome").value("pkg:outcome/account-value@1.0"))
                .andExpect(jsonPath("$.requestId").value("report-1"));
    }

    @Test
    void anUnknownAccountIsRefusedNamingIt() throws Exception {
        mvc.perform(get("/accounts/nobody/value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("no account 'nobody'"));
    }

    @Test
    void aQuoteIsAPlanOfOneStep() throws Exception {
        mvc.perform(get("/quote").param("amount", "732.98").param("from", "GBP").param("to", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("916.22"))
                .andExpect(jsonPath("$.servedBy").value(LOCAL_JAVA))
                .andExpect(jsonPath("$.binding").value("local"));
    }

    @Test
    void aRejectedQuoteKeepsTheServicesReason() throws Exception {
        mvc.perform(get("/quote").param("amount", "1.00").param("from", "USD").param("to", "JPY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Matchers.containsString("USD")));
    }
}
