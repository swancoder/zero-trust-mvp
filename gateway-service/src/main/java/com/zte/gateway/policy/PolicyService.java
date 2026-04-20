package com.zte.gateway.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Evaluates whether a given set of roles may access a path+method combination,
 * consulting the {@link AccessPolicyRepository} with a 5-minute in-process cache.
 *
 * <p>Cache strategy: Reactor {@code Mono.cache(Duration)} — the first subscriber
 * triggers a DB fetch; subsequent subscribers within the TTL receive the cached
 * result. On DB error, an empty policy list is returned (fail-closed: deny all)
 * and the error is NOT cached (next subscriber retries the DB).
 *
 * <p>Calling {@link #isAllowed} is fully non-blocking and safe to use in a
 * WebFlux pipeline.
 */
@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Mono<List<AccessPolicy>> policyCache;

    public PolicyService(AccessPolicyRepository repository) {
        this.policyCache = repository.findByEnabled(true)
                .collectList()
                .doOnNext(p -> log.debug("Policy cache refreshed: {} rules loaded", p.size()))
                .onErrorResume(ex -> {
                    log.error("Failed to load access policies from DB — denying all requests", ex);
                    return Mono.just(List.of()); // fail-closed; do NOT cache the error
                })
                .cache(Duration.ofMinutes(5));
    }

    /**
     * Returns {@code true} if any of the caller's {@code roles} matches an
     * enabled policy for the given {@code path} and {@code method}.
     */
    public Mono<Boolean> isAllowed(List<String> roles, String path, String method) {
        return policyCache.map(policies ->
                policies.stream()
                        .filter(p -> roles.contains(p.roleName()))
                        .filter(p -> methodMatches(p.methods(), method))
                        .anyMatch(p -> PATH_MATCHER.match(p.pathPattern(), path))
        );
    }

    private boolean methodMatches(String policyMethods, String requestMethod) {
        if ("*".equals(policyMethods)) return true;
        return Arrays.asList(policyMethods.split(",")).contains(requestMethod);
    }
}
