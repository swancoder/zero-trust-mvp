package com.zte.serviceb;

import com.zte.auth.ZteSecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Service B — internal ZTE downstream service.
 *
 * <p>Security is enforced at two layers:
 * <ol>
 *   <li><strong>mTLS (transport):</strong> Netty server requires a valid client certificate
 *       signed by the ZTE CA ({@code server.ssl.client-auth=need}).</li>
 *   <li><strong>OBO token (application):</strong> {@code X-ZTE-User-Context} header validated
 *       by {@link com.zte.serviceb.controller.UserContextController} via
 *       {@link com.zte.auth.obo.UserContextTokenService}.</li>
 * </ol>
 *
 * <p>{@link ZteSecurityAutoConfiguration} is excluded — service-b is an internal service
 * that does not accept user-facing Keycloak JWTs. Its trust perimeter is the ZTE CA.
 */
@SpringBootApplication(exclude = ZteSecurityAutoConfiguration.class)
public class ServiceBApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceBApplication.class, args);
    }
}
