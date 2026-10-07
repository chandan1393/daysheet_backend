package com.daysheet.security;

/** What every authenticated request knows about its caller, read straight from the JWT. */
public record AuthPrincipal(Long userId, Long workspaceId, String email, String role) {}
