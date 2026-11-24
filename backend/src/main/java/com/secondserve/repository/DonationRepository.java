package com.secondserve.repository;

import com.secondserve.model.Donation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory authoritative store of donations; keeps registration order (used by the FCFS baseline). */
public final class DonationRepository {
    private final Map<String, Donation> data = new LinkedHashMap<>();
    private final DataVersion version;

    public DonationRepository(DataVersion version) { this.version = version; }

    public synchronized boolean exists(String id) { return data.containsKey(id); }
    public synchronized Optional<Donation> find(String id) { return Optional.ofNullable(data.get(id)); }
    public synchronized List<Donation> findAll() { return new ArrayList<>(data.values()); }
    public synchronized List<String> ids() { return new ArrayList<>(data.keySet()); }
    public synchronized int count() { return data.size(); }
    public synchronized void save(Donation d) { data.put(d.batchId(), d); version.bump(); }
    public synchronized boolean delete(String id) { boolean r = data.remove(id) != null; if (r) version.bump(); return r; }
    public synchronized void clear() { data.clear(); version.bump(); }
}
