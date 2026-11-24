package com.secondserve.repository;

import com.secondserve.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class UserRepository {
    private final Map<String, User> byId = new LinkedHashMap<>();

    public synchronized Optional<User> find(String id) { return Optional.ofNullable(byId.get(id)); }
    public synchronized Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        String u = username.trim().toLowerCase(Locale.ROOT);
        return byId.values().stream().filter(x -> x.username().equals(u)).findFirst();
    }
    public synchronized List<User> findAll() { return new ArrayList<>(byId.values()); }
    public synchronized void save(User u) { byId.put(u.userId(), u); }
    public synchronized int count() { return byId.size(); }
}
