package com.daysheet.security;

/** A logged-in Daysheet admin. Admin tokens can never be used on practice endpoints, and the reverse. */
public record AdminPrincipal(Long adminId, String email) {}
