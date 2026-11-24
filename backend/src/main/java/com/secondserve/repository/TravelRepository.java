package com.secondserve.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Manually entered travel-time estimates (minutes) per donation batch / NGO pair. */
public final class TravelRepository {
    private final Map<String, Integer> minutes = new HashMap<>();
    private final DataVersion version;

    public TravelRepository(DataVersion version) { this.version = version; }

    private static String key(String d, String n) { return d + "|" + n; }

    public synchronized Optional<Integer> get(String donationId, String ngoId) { return Optional.ofNullable(minutes.get(key(donationId, ngoId))); }
    public synchronized void put(String donationId, String ngoId, int m) { minutes.put(key(donationId, ngoId), m); version.bump(); }
    public synchronized void remove(String donationId, String ngoId) { if (minutes.remove(key(donationId, ngoId)) != null) version.bump(); }
    public synchronized void removeDonation(String id) { minutes.keySet().removeIf(k -> k.startsWith(id + "|")); }
    public synchronized void removeNgo(String id) { minutes.keySet().removeIf(k -> k.endsWith("|" + id)); }
    public synchronized void clear() { minutes.clear(); version.bump(); }
    /** NGO id -> minutes for every route entered for one donation batch. */
    public synchronized Map<String, Integer> forDonation(String donationId) {
        Map<String, Integer> out = new HashMap<>();
        String prefix = donationId + "|";
        for (Map.Entry<String, Integer> e : minutes.entrySet()) if (e.getKey().startsWith(prefix)) out.put(e.getKey().substring(prefix.length()), e.getValue());
        return out;
    }
    public synchronized Map<String, Integer> snapshot() { return new HashMap<>(minutes); }
}
