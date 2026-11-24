package com.secondserve.service;

import com.secondserve.exception.ConflictException;
import com.secondserve.exception.ForbiddenException;
import com.secondserve.exception.NotFoundException;
import com.secondserve.exception.UnauthorizedException;
import com.secondserve.exception.ValidationException;
import com.secondserve.model.Ngo;
import com.secondserve.model.Principal;
import com.secondserve.model.Role;
import com.secondserve.model.User;
import com.secondserve.repository.NgoRepository;
import com.secondserve.repository.UserRepository;
import com.secondserve.util.Fields;
import com.secondserve.util.Passwords;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Login, sessions (opaque random bearer tokens, 8 h), self-registration with admin approval, and account administration. */
public final class AuthService {
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,29}$");
    private static final Duration SESSION = Duration.ofHours(8);
    private static final int MAX_FAILS = 5;
    private static final Duration LOCK = Duration.ofSeconds(60);

    /** What the browser receives; never contains the password hash. */
    public record UserView(String userId, String username, String displayName, String role, String orgName, String ngoId, String status, String createdAt) {}
    public record LoginResult(String token, String expiresAt, UserView user) {}

    private record Session(String userId, Instant expires) {}
    private record Attempts(int fails, Instant lockedUntil) {}

    private final UserRepository users;
    private final NgoRepository ngos;
    private final NgoService ngoService;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, Attempts> attempts = new HashMap<>();
    private long idSeq;

    public AuthService(UserRepository users, NgoRepository ngos, NgoService ngoService, AuditService audit, NotificationService notifications) {
        this.users = users; this.ngos = ngos; this.ngoService = ngoService; this.audit = audit; this.notifications = notifications;
    }

    public static UserView view(User u) { return new UserView(u.userId(), u.username(), u.displayName(), u.role().name(), u.orgName(), u.ngoId(), u.status(), u.createdAt()); }

    // ----------------------------------------------------------------- seeding
    public synchronized User createUser(String username, String password, String displayName, Role role, String orgName, String ngoId, String status, User.NgoRequest req) {
        String salt = Passwords.newSalt();
        User u = new User("U-" + (++idSeq), username.toLowerCase(Locale.ROOT), displayName, role, orgName, ngoId, status, salt, Passwords.hash(password, salt),
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), req);
        users.save(u);
        return u;
    }

    /** Fast seeding path for demo accounts that share one demo password (one PBKDF2 computation for the whole batch). */
    public synchronized User seedUser(String username, String displayName, Role role, String orgName, String ngoId, String salt, String hash) {
        User u = new User("U-" + (++idSeq), username, displayName, role, orgName, ngoId, "ACTIVE", salt, hash,
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), null);
        users.save(u);
        return u;
    }

    // ------------------------------------------------------------------- login
    public synchronized LoginResult login(Map<String, Object> body) {
        Fields f = new Fields(body);
        String username = f.str("username", "Username", true, 40);
        String password = f.str("password", "Password", true, 100);
        f.throwIfInvalid();
        String key = username.toLowerCase(Locale.ROOT);
        Attempts a = attempts.get(key);
        Instant now = Instant.now();
        if (a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(now))
            throw new UnauthorizedException("Too many failed attempts. Try again in " + Math.max(1, Duration.between(now, a.lockedUntil()).toSeconds()) + " seconds.");
        Optional<User> found = users.findByUsername(key);
        boolean ok = found.isPresent() && Passwords.matches(password, found.get().salt(), found.get().hash());
        if (!ok) {
            int fails = (a == null || a.lockedUntil() != null ? 0 : a.fails()) + 1;
            attempts.put(key, new Attempts(fails, fails >= MAX_FAILS ? now.plus(LOCK) : null));
            audit.record(key, "-", "LOGIN_FAILED", "Wrong username or password");
            throw new UnauthorizedException("Wrong username or password.");
        }
        User u = found.get();
        if ("PENDING".equals(u.status())) throw new ForbiddenException("Your registration is waiting for admin approval.");
        if (!"ACTIVE".equals(u.status())) throw new ForbiddenException("This account is " + u.status().toLowerCase(Locale.ROOT) + ".");
        attempts.remove(key);
        String token = Passwords.newToken();
        Instant exp = now.plus(SESSION);
        sessions.put(token, new Session(u.userId(), exp));
        audit.record(u.username(), u.role().name(), "LOGIN", "Signed in");
        return new LoginResult(token, exp.toString(), view(u));
    }

    public synchronized void logout(String token) { if (token != null) sessions.remove(token); }

    public static String bearer(String header) {
        if (header == null) return null;
        String h = header.trim();
        return h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
    }

    /** Resolves the Authorization header to the caller, or throws 401. */
    public synchronized Principal authenticate(String authorizationHeader) {
        String token = bearer(authorizationHeader);
        if (token == null || token.isEmpty()) throw new UnauthorizedException("Sign in to continue.");
        Session s = sessions.get(token);
        if (s == null) throw new UnauthorizedException("Your session is not valid. Sign in again.");
        if (s.expires().isBefore(Instant.now())) { sessions.remove(token); throw new UnauthorizedException("Your session has expired. Sign in again."); }
        User u = users.find(s.userId()).orElseThrow(() -> new UnauthorizedException("Account no longer exists."));
        if (!"ACTIVE".equals(u.status())) { sessions.remove(token); throw new UnauthorizedException("This account is " + u.status().toLowerCase(Locale.ROOT) + "."); }
        return new Principal(u.userId(), u.username(), u.displayName(), u.role(), u.orgName(), u.ngoId());
    }

    public UserView me(Principal p) { return view(users.find(p.userId()).orElseThrow(() -> new NotFoundException("Account not found"))); }

    // ------------------------------------------------------------ registration
    public synchronized UserView register(Map<String, Object> body) {
        Fields f = new Fields(body);
        String username = f.str("username", "Username", true, 30);
        String password = f.str("password", "Password", true, 100);
        String display = f.str("displayName", "Your name", true, 60);
        String roleRaw = f.str("role", "Account type", true, 10);
        String org = f.str("orgName", "Organisation name", true, 80);
        Role role = null;
        if (roleRaw != null) {
            try { role = Role.valueOf(roleRaw.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { f.error("Account type must be DONOR or NGO"); }
            if (role == Role.ADMIN) { f.error("Admin accounts cannot be self-registered"); role = null; }
        }
        if (username != null && !USERNAME.matcher(username.toLowerCase(Locale.ROOT)).matches()) f.error("Username must be 3-30 characters: letters, digits, '.', '_' or '-'");
        if (password != null && password.length() < 8) f.error("Password must be at least 8 characters");
        User.NgoRequest req = null;
        if (role == Role.NGO) {
            Integer need = f.integer("requiredMeals", "Required meal packs", 1, 1_000_000);
            Integer pick = f.integer("pickupCapacity", "Maximum pickup capacity", 1, 1_000_000);
            Integer trav = f.integer("maxTravelMinutes", "Maximum acceptable travel time (minutes)", 1, 1440);
            String contact = f.str("contact", "Contact information", false, 100);
            if (need != null && pick != null && trav != null) req = new User.NgoRequest(need, pick, trav, contact == null ? "" : contact);
        }
        f.throwIfInvalid();
        if (users.findByUsername(username).isPresent()) throw new ConflictException("Username '" + username.toLowerCase(Locale.ROOT) + "' is already taken");
        User u = createUser(username, password, display, role, org, null, "PENDING", req);
        audit.record(u.username(), role.name(), "REGISTER", org + " requested a " + role + " account");
        notifications.toAdmins("APPROVAL", "New registration", org + " (" + role + ") is waiting for approval.");
        return view(u);
    }

    // ---------------------------------------------------------- administration
    public List<UserView> listUsers() { return users.findAll().stream().map(AuthService::view).toList(); }

    public synchronized UserView approve(Principal admin, String userId) {
        User u = users.find(userId).orElseThrow(() -> new NotFoundException("User " + userId + " does not exist"));
        if (!"PENDING".equals(u.status())) throw new ConflictException("Only pending registrations can be approved (this one is " + u.status() + ")");
        if (u.role() == Role.NGO && u.ngoRequest() != null) {
            Map<String, Object> m = new HashMap<>();
            m.put("ngoId", nextNgoId()); m.put("name", u.orgName());
            m.put("requiredMeals", u.ngoRequest().requiredMeals()); m.put("pickupCapacity", u.ngoRequest().pickupCapacity());
            m.put("maxTravelMinutes", u.ngoRequest().maxTravelMinutes()); m.put("contact", u.ngoRequest().contact());
            Ngo n = ngoService.create(m);
            u = u.withNgoId(n.ngoId());
        }
        u = u.withStatus("ACTIVE");
        users.save(u);
        audit.record(admin.username(), "ADMIN", "APPROVE_USER", u.username() + " (" + u.orgName() + ")" + (u.ngoId() != null ? " -> NGO " + u.ngoId() : ""));
        notifications.toUser(u.userId(), "ACCOUNT", "Account approved", "Welcome to SecondServe. You can now use your dashboard.");
        return view(u);
    }

    public synchronized UserView reject(Principal admin, String userId) {
        User u = users.find(userId).orElseThrow(() -> new NotFoundException("User " + userId + " does not exist"));
        if (!"PENDING".equals(u.status())) throw new ConflictException("Only pending registrations can be rejected (this one is " + u.status() + ")");
        u = u.withStatus("REJECTED");
        users.save(u);
        audit.record(admin.username(), "ADMIN", "REJECT_USER", u.username() + " (" + u.orgName() + ")");
        return view(u);
    }

    public synchronized UserView setEnabled(Principal admin, String userId, boolean enabled) {
        User u = users.find(userId).orElseThrow(() -> new NotFoundException("User " + userId + " does not exist"));
        if (u.userId().equals(admin.userId())) throw new ConflictException("You cannot disable your own account");
        if (enabled && !"DISABLED".equals(u.status())) throw new ConflictException("Only disabled accounts can be re-enabled");
        if (!enabled && !"ACTIVE".equals(u.status())) throw new ConflictException("Only active accounts can be disabled");
        u = u.withStatus(enabled ? "ACTIVE" : "DISABLED");
        users.save(u);
        if (!enabled) sessions.values().removeIf(s -> s.userId().equals(userId));
        audit.record(admin.username(), "ADMIN", enabled ? "ENABLE_USER" : "DISABLE_USER", u.username());
        return view(u);
    }

    public synchronized void changePassword(Principal who, Map<String, Object> body) {
        Fields f = new Fields(body);
        String oldPw = f.str("oldPassword", "Current password", true, 100);
        String newPw = f.str("newPassword", "New password", true, 100);
        f.throwIfInvalid();
        if (newPw.length() < 8) throw new ValidationException("New password must be at least 8 characters");
        User u = users.find(who.userId()).orElseThrow(() -> new NotFoundException("Account not found"));
        if (!Passwords.matches(oldPw, u.salt(), u.hash())) throw new UnauthorizedException("Current password is wrong.");
        String salt = Passwords.newSalt();
        users.save(u.withPassword(salt, Passwords.hash(newPw, salt)));
        audit.record(u.username(), u.role().name(), "CHANGE_PASSWORD", "Password changed");
    }

    private String nextNgoId() {
        int n = ngos.count() + 1;
        while (ngos.exists(String.format("N-%02d", n))) n++;
        return String.format("N-%02d", n);
    }
}
