package com.secondserve.repository;

import com.secondserve.model.Delivery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory store of committed deliveries. Every change bumps the data version so a stored allocation is flagged stale. */
public final class DeliveryRepository {
    private final Map<String, Delivery> data = new LinkedHashMap<>();
    private final DataVersion version;

    public DeliveryRepository(DataVersion version) { this.version = version; }

    public synchronized Optional<Delivery> find(String id) { return Optional.ofNullable(data.get(id)); }
    public synchronized List<Delivery> findAll() { return new ArrayList<>(data.values()); }
    public synchronized void save(Delivery d) { data.put(d.deliveryId(), d); version.bump(); }
    public synchronized void clear() { data.clear(); version.bump(); }

    /** Meals held per donation batch by deliveries that are not FAILED. */
    public synchronized Map<String, Integer> heldByBatch() {
        Map<String, Integer> m = new HashMap<>();
        for (Delivery d : data.values()) if (d.holdsMeals()) m.merge(d.batchId(), d.meals(), Integer::sum);
        return m;
    }

    /** Meals held per NGO by deliveries that are not FAILED. */
    public synchronized Map<String, Integer> heldByNgo() {
        Map<String, Integer> m = new HashMap<>();
        for (Delivery d : data.values()) if (d.holdsMeals()) m.merge(d.ngoId(), d.meals(), Integer::sum);
        return m;
    }
}
