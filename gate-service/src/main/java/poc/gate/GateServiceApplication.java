package poc.gate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point. @SpringBootApplication scans poc.gate.* for beans; the scan below finds GateProperties. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GateServiceApplication.class, args);
    }
}
