package com.zte.serviceb.config;

import com.zte.auth.obo.UserContextTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link UserContextTokenService} bean for service-b.
 *
 * <p>service-b uses this to validate the {@code X-ZTE-User-Context} header
 * on every inbound request. The same HMAC secret must be configured in all
 * services — see {@code ZTE_OBO_SECRET} env var.
 */
@Configuration
public class UserContextConfig {

    @Bean
    public UserContextTokenService userContextTokenService(
            @Value("${zte.obo.secret}") String secret,
            @Value("${zte.obo.expiry-seconds:30}") long expirySeconds) {
        return new UserContextTokenService(secret, expirySeconds);
    }
}
