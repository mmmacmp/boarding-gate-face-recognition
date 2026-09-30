package poc.gate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. @SpringBootApplication scans poc.gate.* for beans; @ConfigurationPropertiesScan finds GateProperties;
 * @EnableScheduling activates @Scheduled methods (PurgeJob).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GateServiceApplication.class, args);
    }
}
