package tn.oliveconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class LoggingConfigurationTest {

    @Test
    void productionLoggingUsesStructuredRollingFilesWithMdcProperties() throws IOException {
        String configuration = StreamUtils.copyToString(
                new ClassPathResource("log4j2-spring.xml").getInputStream(), StandardCharsets.UTF_8);

        assertThat(configuration)
                .contains("<SpringProfile name=\"prod\">")
                .contains("<RollingFile name=\"RollingFile\"")
                .contains("${env:LOG_DIR:-logs}/oliveconnect.json")
                .contains("<JsonLayout")
                .contains("properties=\"true\"")
                .contains("<SizeBasedTriggeringPolicy size=\"100 MB\"/>")
                .contains("<DefaultRolloverStrategy max=\"14\"/>");
    }
}
