package poc.gate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** With the default configuration (demo.xray=false) the demo X-ray features do not exist. */
@AutoConfigureMockMvc
class DemoXrayDisabledIT extends AbstractGateIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void demoEndpointsReturn404ByDefault() throws Exception {
        mvc.perform(get("/v1/demo/flights/POC001/gallery")).andExpect(status().isNotFound());
        mvc.perform(post("/v1/demo/breakers/reset")).andExpect(status().isNotFound());
    }

    @Test
    void gateEventResponseHasNoXrayBlockByDefault() throws Exception {
        String flight = boardingFlight(passengers(unique("A"), unit(1, 0, 0, 0)));
        mvc.perform(multipart("/v1/gates/G1/events")
                        .file(new MockMultipartFile("image", "c.jpg", "image/jpeg", probe(unit(1, 0, 0, 0))))
                        .param("flight_id", flight)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BOARD"))
                .andExpect(jsonPath("$.xray").doesNotExist());
    }
}
