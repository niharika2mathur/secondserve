package com.secondserve.standalone;

import com.secondserve.exception.*;
import com.secondserve.model.Principal;
import com.secondserve.service.AccessPolicy;
import com.secondserve.service.AppContext;
import com.secondserve.service.AuthService;
import com.secondserve.util.Fields;
import com.secondserve.util.MiniJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free HTTP server exposing exactly the same REST API as the Spring Boot controllers, built on the JDK's
 * com.sun.net.httpserver. It exists so the project can be run and verified where Maven Central is unreachable;
 * the primary (assessed) backend is the Spring Boot application. Both call the same service classes.
 *
 * Run: java -cp target/classes com.secondserve.standalone.StandaloneServer [port]
 */
public final class StandaloneServer {
    private static final Pattern ID_ROUTE = Pattern.compile("^/api/(donations|ngos)/([^/]+)$");
    private static final Pattern MY_DONATION = Pattern.compile("^/api/my/donations/([^/]+)$");
    private static final Pattern DELIVERY_ACTION = Pattern.compile("^/api/deliveries/([^/]+)/(handover|confirm|report)$");
    private static final Pattern USER_ACTION = Pattern.compile("^/api/admin/users/([^/]+)/(approve|reject|disable|enable)$");

    private final AppContext ctx;

    public StandaloneServer(AppContext ctx) { this.ctx = ctx; }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        AppContext ctx = new AppContext(1000, 0.01, true);
        StandaloneServer s = new StandaloneServer(ctx);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", s::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("SecondServe standalone backend listening on http://localhost:" + port);
    }

    @SuppressWarnings("unchecked")
    private void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        int status = 200;
        Object body;
        try {
            String method = ex.getRequestMethod();
            if (method.equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = query(ex.getRequestURI().getRawQuery());
            String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> json = null;
            if (!raw.isBlank()) {
                try { Object p = MiniJson.parse(raw); if (p instanceof Map<?, ?> m) json = (Map<String, Object>) m; else throw new IllegalArgumentException("JSON object expected"); }
                catch (RuntimeException e) { throw new ValidationException("Request body is missing or is not valid JSON"); }
            }
            String authHeader = ex.getRequestHeaders().getFirst("Authorization");
            Principal who = null;
            if (!AccessPolicy.isPublic(method, path)) {
                who = ctx.auth.authenticate(authHeader);        // 401 when missing/invalid
                AccessPolicy.check(method, path, who);            // 403 when the role may not use this route
            }
            Object[] out = route(method, path, query, json, who, authHeader);
            status = (Integer) out[0];
            body = out[1];
        } catch (ValidationException e) { status = 400; body = new ApiError(400, "Bad Request", e.getMessage(), e.getErrors()); }
        catch (UnauthorizedException e) { status = 401; body = new ApiError(401, "Unauthorized", e.getMessage(), List.of()); }
        catch (ForbiddenException e) { status = 403; body = new ApiError(403, "Forbidden", e.getMessage(), List.of()); }
        catch (NotFoundException e) { status = 404; body = new ApiError(404, "Not Found", e.getMessage(), List.of()); }
        catch (ConflictException e) { status = 409; body = new ApiError(409, "Conflict", e.getMessage(), List.of()); }
        catch (Exception e) { e.printStackTrace(); status = 500; body = new ApiError(500, "Internal Server Error", "Unexpected server error: " + e.getClass().getSimpleName(), List.of()); }
        if (body == null) { ex.sendResponseHeaders(status, -1); ex.close(); return; }
        byte[] bytes = MiniJson.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private Object[] route(String method, String path, Map<String, String> q, Map<String, Object> body, Principal who, String authHeader) {
        Matcher m = ID_ROUTE.matcher(path);
        if (m.matches() && !path.equals("/api/donations/import-csv")) {
            String kind = m.group(1), id = java.net.URLDecoder.decode(m.group(2), StandardCharsets.UTF_8);
            if (method.equals("PUT")) return ok(kind.equals("donations") ? ctx.donationService.update(id, body) : ctx.ngoService.update(id, body));
            if (method.equals("DELETE")) { if (kind.equals("donations")) ctx.donationService.delete(id); else ctx.ngoService.delete(id); return new Object[]{204, null}; }
        }
        Matcher md = MY_DONATION.matcher(path);
        if (md.matches()) {
            String id = java.net.URLDecoder.decode(md.group(1), StandardCharsets.UTF_8);
            if (method.equals("PUT")) return ok(ctx.portal.updateDonation(who, id, body));
            if (method.equals("DELETE")) { ctx.portal.deleteDonation(who, id); return new Object[]{204, null}; }
        }
        Matcher da = DELIVERY_ACTION.matcher(path);
        if (da.matches() && method.equals("POST")) {
            String id = java.net.URLDecoder.decode(da.group(1), StandardCharsets.UTF_8);
            return ok(switch (da.group(2)) {
                case "handover" -> ctx.dispatch.handover(who, id);
                case "confirm" -> ctx.dispatch.confirm(who, id, body);
                default -> ctx.dispatch.report(who, id, body);
            });
        }
        Matcher ua = USER_ACTION.matcher(path);
        if (ua.matches() && method.equals("POST")) {
            String id = java.net.URLDecoder.decode(ua.group(1), StandardCharsets.UTF_8);
            return ok(switch (ua.group(2)) {
                case "approve" -> ctx.auth.approve(who, id);
                case "reject" -> ctx.auth.reject(who, id);
                case "disable" -> ctx.auth.setEnabled(who, id, false);
                default -> ctx.auth.setEnabled(who, id, true);
            });
        }
        switch (method + " " + path) {
            // ---- authentication
            case "POST /api/auth/login": return ok(ctx.auth.login(body));
            case "POST /api/auth/register": return new Object[]{201, ctx.auth.register(body)};
            case "POST /api/auth/logout": ctx.auth.logout(AuthService.bearer(authHeader)); return ok(Map.of("status", "ok"));
            case "GET /api/auth/me": return ok(ctx.auth.me(who));
            case "POST /api/auth/password": ctx.auth.changePassword(who, body); return ok(Map.of("status", "ok"));
            // ---- notifications
            case "GET /api/notifications": return ok(ctx.notifications.list(who.userId()));
            case "POST /api/notifications/read-all": ctx.notifications.markAllRead(who.userId()); return ok(Map.of("status", "ok"));
            // ---- donor and NGO self-service
            case "GET /api/my/donations": return ok(ctx.portal.myDonations(who));
            case "POST /api/my/donations": return new Object[]{201, ctx.portal.createDonation(who, body)};
            case "GET /api/my/ngo": return ok(ctx.portal.myNgo(who));
            case "PUT /api/my/ngo": return ok(ctx.portal.updateMyNgo(who, body));
            case "GET /api/my/deliveries": return ok(ctx.dispatch.list(who));
            // ---- role dashboards
            case "GET /api/dashboard/donor": return ok(ctx.roleDashboards.donor(who));
            case "GET /api/dashboard/ngo": return ok(ctx.roleDashboards.ngo(who));
            case "GET /api/dashboard/admin": return ok(ctx.roleDashboards.admin(who));
            // ---- admin: dispatch, users, audit, analytics, insights
            case "POST /api/dispatch/commit": return ok(ctx.dispatch.commit(who));
            case "GET /api/deliveries": return ok(ctx.dispatch.list(who));
            case "GET /api/admin/users": return ok(ctx.auth.listUsers());
            case "GET /api/admin/audit": return ok(ctx.audit.recent(200));
            case "GET /api/analytics": return ok(ctx.analytics.summary());
            case "GET /api/insights/recommendations": return ok(ctx.insights.recommendations(Fields.parseTime(q.get("planningTime"), "Planning date and time")));
            case "POST /api/insights/what-if": return ok(ctx.insights.whatIf(body));
            // ---- original admin routes
            case "GET /api/dashboard": return ok(ctx.dashboardService.build());
            case "GET /api/donations": return ok(ctx.donationService.list());
            case "POST /api/donations": return new Object[]{201, ctx.donationService.create(body)};
            case "POST /api/donations/import-csv": return ok(ctx.donationService.importCsv(body == null || body.get("csv") == null ? null : String.valueOf(body.get("csv"))));
            case "GET /api/ngos": return ok(ctx.ngoService.list());
            case "POST /api/ngos": return new Object[]{201, ctx.ngoService.create(body)};
            case "GET /api/travel": return ok(ctx.travelService.view());
            case "PUT /api/travel": return ok(ctx.travelService.update(body));
            case "GET /api/feasibility": return ok(ctx.feasibilityService.view(Fields.parseTime(q.get("planningTime"), "Planning date and time")));
            case "POST /api/allocation/run": return ok(ctx.allocationService.runRequest(body));
            case "GET /api/allocation/results": return ok(ctx.allocationService.results());
            case "GET /api/algorithms/network-analysis": return ok(ctx.allocationService.analysis());
            case "GET /api/algorithms/bloom-stats": return ok(ctx.bloomService.stats());
            case "POST /api/algorithms/bloom-check": return ok(ctx.bloomService.check(body));
            case "POST /api/algorithms/bloom-demo": return ok(ctx.bloomService.demo(body));
            case "POST /api/algorithms/benchmarks": return ok(ctx.benchmarkService.runRequest(body));
            case "GET /api/algorithms/benchmarks/latest": {
                Object r = ctx.benchmarkService.latest();
                if (r == null) throw new NotFoundException("No benchmark has been run in this session yet");
                return ok(r);
            }
            case "POST /api/data/reset": ctx.reset(body != null && "empty".equals(body.get("mode")) ? "empty" : "sample"); return ok(Map.of("status", "ok"));
            default: throw new NotFoundException("No route for " + method + " " + path);
        }
    }

    private static Object[] ok(Object o) { return new Object[]{200, o}; }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) m.put(java.net.URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8), java.net.URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }
}
