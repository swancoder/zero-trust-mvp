package com.zte.gateway.policy;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Reactive R2DBC repository for {@link AccessPolicy}.
 *
 * <p>Uses non-blocking I/O — safe to call from WebFlux request handlers
 * without blocking a Netty event-loop thread.
 */
@Repository
public interface AccessPolicyRepository extends ReactiveCrudRepository<AccessPolicy, Long> {

    /** Returns all policies with the given enabled state. */
    Flux<AccessPolicy> findByEnabled(boolean enabled);
}
