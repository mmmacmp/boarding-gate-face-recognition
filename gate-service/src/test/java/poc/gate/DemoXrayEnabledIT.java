package poc.gate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** With demo.xray=true: per-candidate scores in the event response, and the gallery states from the database. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "demo.xray=true")
class DemoXrayEnabledIT extends AbstractGateIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void eventResponseCarriesAllScoresAndTheFaceBox() throws Exception {
        String a = unique("A");
        String b = unique("B");
        String flight = boardingFlight(passengers(a, unit(1, 0, 0, 0), b, unit(0, 1, 0, 0)));
        mvc.perform(multipart("/v1/gates/G1/events")
                        .file(new MockMultipartFile("image", "c.jpg", "image/jpeg", probe(unit(1, 0.1, 0, 0))))
                        .param("flight_id", flight)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BOARD"))
                .andExpect(jsonPath("$.xray.scores." + a).isNumber())
                .andExpect(jsonPath("$.xray.scores." + b).isNumber())
                .andExpect(jsonPath("$.xray.face_boxes[0].length()").value(4));
    }

    @Test
    void galleryShowsActiveBoardedAndWithdrawnFromTheDatabase() throws Exception {
        String a = unique("A");
        String b = unique("B");
        String c = unique("C");
        String flight = boardingFlight(passengers(a, unit(1, 0, 0, 0), b, unit(0, 1, 0, 0), c, unit(0, 0, 1, 0)));
        event("G1", flight, probe(unit(1, 0, 0, 0)));   // a boards
        consents.withdraw(c);                          // c opts out

        mvc.perform(get("/v1/demo/flights/" + flight + "/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flight_status").value("BOARDING"))
                .andExpect(jsonPath("$.entries[0].status").value("BOARDED"))
                .andExpect(jsonPath("$.entries[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.entries[2].status").value("CONSENT_WITHDRAWN"))
                .andExpect(jsonPath("$.entries[0].is_synthetic").value(false));
    }
}
