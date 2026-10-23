package com.insurance.renewal.security;

import java.lang.annotation.*;

/**
 * Lightweight role-gate for demo/dev purposes.
 * Reads the "X-User-Role" header sent by the Angular client and checks it
 * against the roles listed here. In production, REPLACE this with proper
 * Spring Security + JWT (roles derived from a verified token, not a
 * client-supplied header).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    String[] value(); // e.g. {"UNDERWRITER", "BRANCH_MANAGER"}
}
