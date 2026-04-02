package com.zte.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration entry point for the auth-library.
 *
 * <p>Services that depend on {@code :auth-library} automatically receive
 * {@link SecurityConfig} without any explicit {@code @Import} on their side,
 * as long as this class is registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@Import(SecurityConfig.class)
public class ZteSecurityAutoConfiguration {
    // Marker class — configuration is delegated to SecurityConfig
}
