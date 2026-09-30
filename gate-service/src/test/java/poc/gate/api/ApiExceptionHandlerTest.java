package poc.gate.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import poc.gate.events.GateDecisionService;

/** Failure-mode row "DB down": only the web layer is started (no DB), the service is a Mockito mock. */
@WebMvcTest(GateController.class)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private GateDecisionService service;

    @Test
    void databaseDownReturns503WithAManualDecision() throws Exception {
        when(service.handle(anyString(), anyString(), anyString(), any()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        mvc.perform(multipart("/v1/gates/G1/events")
                        .file(new MockMultipartFile("image", "capture.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .param("flight_id", "POC001")
                        .header("Idempotency-Key", "k-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.decision").value("MANUAL"))
                .andExpect(jsonPath("$.reason").value("DB_UNAVAILABLE"));
    }
}
