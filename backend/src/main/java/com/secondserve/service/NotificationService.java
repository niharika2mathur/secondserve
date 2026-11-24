package com.secondserve.service;

import com.secondserve.model.Role;
import com.secondserve.model.User;
import com.secondserve.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** In-app notifications per user (no e-mail or SMS: external services are out of scope). */
public final class NotificationService {
    public record Notification(long id, String time, String type, String title, String message, boolean read) {}
    private static final int MAX_PER_USER = 60;

    private final UserRepository users;
    private final Map<String, List<Notification>> byUser = new HashMap<>();
    private long seq;

    public NotificationService(UserRepository users) { this.users = users; }

    public synchronized void toUser(String userId, String type, String title, String message) {
        List<Notification> l = byUser.computeIfAbsent(userId, k -> new ArrayList<>());
        l.add(new Notification(++seq, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), type, title, message, false));
        if (l.size() > MAX_PER_USER) l.remove(0);
    }

    public void toDonor(String donorName, String type, String title, String message) {
        for (User u : users.findAll()) if (u.role() == Role.DONOR && u.orgName() != null && u.orgName().equalsIgnoreCase(donorName) && "ACTIVE".equals(u.status())) toUser(u.userId(), type, title, message);
    }

    public void toNgo(String ngoId, String type, String title, String message) {
        for (User u : users.findAll()) if (u.role() == Role.NGO && ngoId != null && ngoId.equals(u.ngoId()) && "ACTIVE".equals(u.status())) toUser(u.userId(), type, title, message);
    }

    public void toAdmins(String type, String title, String message) {
        for (User u : users.findAll()) if (u.role() == Role.ADMIN && "ACTIVE".equals(u.status())) toUser(u.userId(), type, title, message);
    }

    /** Newest first. */
    public synchronized List<Notification> list(String userId) {
        List<Notification> l = new ArrayList<>(byUser.getOrDefault(userId, List.of()));
        Collections.reverse(l);
        return l;
    }

    public synchronized int unread(String userId) {
        int c = 0;
        for (Notification n : byUser.getOrDefault(userId, List.of())) if (!n.read()) c++;
        return c;
    }

    public synchronized void markAllRead(String userId) {
        List<Notification> l = byUser.get(userId);
        if (l == null) return;
        l.replaceAll(n -> new Notification(n.id(), n.time(), n.type(), n.title(), n.message(), true));
    }
}
