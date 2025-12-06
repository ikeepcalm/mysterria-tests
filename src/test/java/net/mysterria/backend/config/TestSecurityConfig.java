package net.mysterria.backend.config;

import net.mysterria.backend.security.InternalApiKeyAuthenticationFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestSecurityConfig {

    @MockBean
    private InternalApiKeyAuthenticationFilter internalApiKeyAuthenticationFilter;

    @Bean
    @Primary
    public InternalApiProperties testInternalApiProperties() {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setApiKey("test-api-key");
        return properties;
    }
}
