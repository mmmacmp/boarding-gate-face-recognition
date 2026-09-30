package poc.gate.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * DEMO ONLY (X-ray view): the detected face boxes and every candidate's score, keyed by profile id. Filled in while an
 * event runs, never stored or logged; GateController only puts it in the response when demo.xray=true.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class XrayDetails {

    public List<List<Double>> faceBoxes = List.of(); // x1, y1, x2, y2 per face in the zone
    public Map<String, Double> scores = new LinkedHashMap<>(); // best first
}
