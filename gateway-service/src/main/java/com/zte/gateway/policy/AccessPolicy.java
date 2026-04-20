package com.zte.gateway.policy;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Immutable representation of a single DB-backed access policy rule.
 *
 * <p>Evaluated by {@link ZteAuthorizationFilter} on every authenticated request.
 * A request is allowed when at least one enabled policy matches all three
 * dimensions: role, path pattern (Ant-style), and HTTP method.
 */
@Table("access_policies")
public record AccessPolicy(
        @Id                        Long    id,
        @Column("role_name")       String  roleName,
        @Column("path_pattern")    String  pathPattern,
                                   String  methods,
                                   boolean enabled
) {}
