package com.secondserve.model;

/**
 * An account. DONOR accounts are linked to a donor organisation by name (matching Donation.donorName); NGO accounts are
 * linked to an Ngo record by id. status: ACTIVE, PENDING (self-registered, waiting for admin approval), REJECTED, DISABLED.
 * ngoRequest holds the intake figures an NGO typed in when registering; the Ngo record is created on approval.
 */
public record User(String userId, String username, String displayName, Role role, String orgName, String ngoId, String status,
                   String salt, String hash, String createdAt, NgoRequest ngoRequest) {

    public record NgoRequest(int requiredMeals, int pickupCapacity, int maxTravelMinutes, String contact) {}

    public User withStatus(String s) { return new User(userId, username, displayName, role, orgName, ngoId, s, salt, hash, createdAt, ngoRequest); }
    public User withNgoId(String id) { return new User(userId, username, displayName, role, orgName, id, status, salt, hash, createdAt, ngoRequest); }
    public User withPassword(String newSalt, String newHash) { return new User(userId, username, displayName, role, orgName, ngoId, status, newSalt, newHash, createdAt, ngoRequest); }
}
