package com.zte.gateway.config;

import com.zte.auth.obo.UserContextTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the {@link UserContextTokenService} bean used by
 * {@link com.zte.gateway.filter.UserContextPropagationFilter} to create
 * signed {@code X-ZTE-User-Context} on-behalf-of tokens.
 *
 * <p>The shared HMAC secret is injected from {@code ZTE_OBO_SECRET} env var.
 * For production, rotate this secret and consider upgrading to RS256.
 */
@Configuration
public class OboTokenConfig {

    @Bean
    public UserContextTokenService userContextTokenService(
            @Value("${zte.obo.secret}") String secret,
            @Value("${zte.obo.expiry-seconds:30}") long expirySeconds) {
        return new UserContextTokenService(secret, expirySeconds);
    }
}
