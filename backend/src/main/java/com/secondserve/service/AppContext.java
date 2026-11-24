package com.secondserve.service;

import com.secondserve.model.Role;
import com.secondserve.repository.*;

/** Plain-Java wiring of repositories and services; used by Spring (as a bean) and by the standalone server. */
public final class AppContext {
    public final DataVersion version = new DataVersion();
    public final DonationRepository donationRepo = new DonationRepository(version);
    public final NgoRepository ngoRepo = new NgoRepository(version);
    public final TravelRepository travelRepo = new TravelRepository(version);
    public final DuplicateScreen screening;
    public final DonationService donationService;
    public final NgoService ngoService;
    public final TravelService travelService;
    public final FeasibilityService feasibilityService;
    public final AllocationService allocationService;
    public final BloomService bloomService;
    public final DashboardService dashboardService;
    public final BenchmarkService benchmarkService = new BenchmarkService();
    public final SampleDataService sampleData;
    // ---- roles, dispatch, analytics (added for the multi-role version)
    public final UserRepository userRepo = new UserRepository();
    public final DeliveryRepository deliveryRepo = new DeliveryRepository(version);
    public final AuditService audit = new AuditService();
    public final NotificationService notifications = new NotificationService(userRepo);
    public final AuthService auth;
    public final DispatchService dispatch;
    public final PortalService portal;
    public final AnalyticsService analytics;
    public final InsightService insights;
    public final RoleDashboardService roleDashboards;

    public AppContext(int bloomExpectedInsertions, double bloomFalsePositiveRate, boolean loadSample) {
        screening = new DuplicateScreen(bloomExpectedInsertions, bloomFalsePositiveRate, donationRepo::exists);
        donationService = new DonationService(donationRepo, travelRepo, screening);
        ngoService = new NgoService(ngoRepo, travelRepo);
        travelService = new TravelService(donationRepo, ngoRepo, travelRepo);
        feasibilityService = new FeasibilityService(donationRepo, ngoRepo, travelRepo);
        allocationService = new AllocationService(donationRepo, ngoRepo, feasibilityService, version);
        bloomService = new BloomService(screening, donationRepo);
        dashboardService = new DashboardService(donationRepo, ngoRepo, allocationService, bloomService, screening);
        sampleData = new SampleDataService(donationRepo, ngoRepo, travelRepo);
        auth = new AuthService(userRepo, ngoRepo, ngoService, audit, notifications);
        dispatch = new DispatchService(deliveryRepo, allocationService, donationRepo, notifications, audit);
        allocationService.setCommitments(dispatch);
        portal = new PortalService(donationService, ngoService, donationRepo, ngoRepo, travelRepo, deliveryRepo, audit, notifications);
        analytics = new AnalyticsService(deliveryRepo);
        insights = new InsightService(allocationService, feasibilityService);
        roleDashboards = new RoleDashboardService(donationRepo, ngoRepo, travelRepo, deliveryRepo, userRepo, allocationService, feasibilityService, analytics, notifications);
        seedAccounts(loadSample);
        if (loadSample) reset("sample");
    }

    public static final String ADMIN_PASSWORD = "admin123", DEMO_PASSWORD = "demo123";

    /** Demo accounts. Admin always exists; donor and NGO demo accounts are linked to the sample organisations. */
    private void seedAccounts(boolean demoUsers) {
        auth.createUser("admin", ADMIN_PASSWORD, "Coordinator", Role.ADMIN, null, null, "ACTIVE", null);
        if (!demoUsers) return;
        String salt = com.secondserve.util.Passwords.newSalt(), hash = com.secondserve.util.Passwords.hash(DEMO_PASSWORD, salt); // one PBKDF2 run for all demo users
        String[][] donors = {{"orchid", "Grand Orchid Hotel"}, {"xavier", "St. Xavier's College Canteen"}, {"spice", "Spice Route Restaurant"},
                {"lakeview", "Lakeview Convention Centre"}, {"sunrise", "Sunrise Bakery & Cafe"}, {"techpark", "TechPark Cafeteria"},
                {"meridian", "Hotel Meridian"}, {"annapurna", "Annapurna Mess"}};
        for (String[] d : donors) auth.seedUser(d[0], d[1] + " (manager)", Role.DONOR, d[1], null, salt, hash);
        String[][] ngos = {{"asha", "N-01", "Asha Community Kitchen"}, {"hope", "N-02", "Hope Shelter Home"}, {"rotibank", "N-03", "Roti Bank Foundation"},
                {"littlestars", "N-04", "Little Stars Orphanage"}, {"seva", "N-05", "Seva Night Shelter"}, {"greenfield", "N-06", "Greenfield Elder Care"},
                {"streetkids", "N-07", "Street Kids Learning Centre"}};
        for (String[] n : ngos) auth.seedUser(n[0], n[2] + " (volunteer)", Role.NGO, n[2], n[1], salt, hash);
    }

    /** mode = "sample" (synthetic demo data) or "empty" (no records; useful to demonstrate validation). */
    public void reset(String mode) {
        if ("empty".equals(mode)) sampleData.clearAll(); else sampleData.load();
        deliveryRepo.clear();
        screening.rebuild(donationRepo.ids());
        allocationService.clear();
    }
}
