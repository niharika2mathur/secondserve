package com.secondserve.model;

/** The authenticated caller, resolved from the session token for every request. */
public record Principal(String userId, String username, String displayName, Role role, String orgName, String ngoId) {}
