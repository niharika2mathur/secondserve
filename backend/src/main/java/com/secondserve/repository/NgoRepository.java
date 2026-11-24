package com.secondserve.repository;

import com.secondserve.model.Ngo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NgoRepository {
    private final Map<String, Ngo> data = new LinkedHashMap<>();
    private final DataVersion version;

    public NgoRepository(DataVersion version) { this.version = version; }

    public synchronized boolean exists(String id) { return data.containsKey(id); }
    public synchronized Optional<Ngo> find(String id) { return Optional.ofNullable(data.get(id)); }
    public synchronized List<Ngo> findAll() { return new ArrayList<>(data.values()); }
    public synchronized int count() { return data.size(); }
    public synchronized void save(Ngo n) { data.put(n.ngoId(), n); version.bump(); }
    public synchronized boolean delete(String id) { boolean r = data.remove(id) != null; if (r) version.bump(); return r; }
    public synchronized void clear() { data.clear(); version.bump(); }
}
