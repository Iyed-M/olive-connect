package tn.oliveconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OliveConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(OliveConnectApplication.class, args);
    }
}
