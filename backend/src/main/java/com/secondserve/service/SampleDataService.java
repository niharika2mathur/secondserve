package com.secondserve.service;

import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.repository.DonationRepository;
import com.secondserve.repository.NgoRepository;
import com.secondserve.repository.TravelRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Realistic synthetic data. All times are relative to "now" so the demo is always inside its planning window. */
public final class SampleDataService {
    private static final String[] NGO_IDS = {"N-01", "N-02", "N-03", "N-04", "N-05", "N-06", "N-07"};
    private static final String[] BATCH_IDS = {"BATCH-101", "BATCH-102", "BATCH-103", "BATCH-104", "BATCH-105", "BATCH-106", "BATCH-107", "BATCH-108"};
    /** Travel minutes, rows = batches, columns = NGOs; 0 = no estimate entered. */
    static final int[][] TRAVEL = {
            /* 101 */ {20, 25, 50, 0, 0, 0, 0},
            /* 102 */ {30, 0, 0, 0, 0, 0, 0},
            /* 103 */ {0, 0, 0, 20, 25, 70, 0},
            /* 104 */ {45, 0, 30, 0, 30, 0, 35},
            /* 105 */ {0, 0, 45, 15, 0, 25, 0},
            /* 106 */ {0, 40, 40, 0, 0, 0, 30},
            /* 107 */ {15, 0, 0, 0, 0, 0, 0},
            /* 108 */ {0, 20, 0, 0, 30, 0, 25},
    };

    private final DonationRepository donations;
    private final NgoRepository ngos;
    private final TravelRepository travel;

    public SampleDataService(DonationRepository d, NgoRepository n, TravelRepository t) { donations = d; ngos = n; travel = t; }

    public void clearAll() { donations.clear(); ngos.clear(); travel.clear(); }

    public void load() {
        clearAll();
        LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        base = base.minusMinutes(base.getMinute() % 15);
        add("BATCH-101", "Grand Orchid Hotel", 120, base.minusMinutes(60), base.plusMinutes(210), "Buffet surplus, veg + non-veg packs");
        add("BATCH-102", "St. Xavier's College Canteen", 80, base.minusMinutes(30), base.plusMinutes(120), "Lunch packs, vegetarian");
        add("BATCH-103", "Spice Route Restaurant", 60, base, base.plusMinutes(60), "Dinner service overproduction");
        add("BATCH-104", "Lakeview Convention Centre", 260, base.minusMinutes(45), base.plusMinutes(240), "Wedding reception surplus");
        add("BATCH-105", "Sunrise Bakery & Cafe", 50, base, base.plusMinutes(40), "Sandwich and pulao packs");
        add("BATCH-106", "TechPark Cafeteria", 90, base.minusMinutes(15), base.plusMinutes(180), "Corporate lunch surplus");
        add("BATCH-107", "Hotel Meridian", 70, base.minusMinutes(120), base.minusMinutes(10), "Late entry - deadline already passed");
        add("BATCH-108", "Annapurna Mess", 40, base.plusMinutes(60), base.plusMinutes(200), "Available after the evening shift");
        ngo("N-01", "Asha Community Kitchen", 150, 120, 40, "asha.kitchen@example.org");
        ngo("N-02", "Hope Shelter Home", 100, 100, 30, "+91 90000 00002");
        ngo("N-03", "Roti Bank Foundation", 180, 150, 45, "rotibank@example.org");
        ngo("N-04", "Little Stars Orphanage", 70, 70, 25, "");
        ngo("N-05", "Seva Night Shelter", 120, 90, 35, "+91 90000 00005");
        ngo("N-06", "Greenfield Elder Care", 60, 60, 30, "");
        ngo("N-07", "Street Kids Learning Centre", 90, 60, 40, "skl@example.org");
        for (int i = 0; i < BATCH_IDS.length; i++)
            for (int j = 0; j < NGO_IDS.length; j++) if (TRAVEL[i][j] > 0) travel.put(BATCH_IDS[i], NGO_IDS[j], TRAVEL[i][j]);
    }

    private void add(String id, String donor, int meals, LocalDateTime from, LocalDateTime until, String notes) {
        donations.save(new Donation(id, donor, meals, from, until, notes));
    }

    private void ngo(String id, String name, int req, int pick, int max, String contact) {
        ngos.save(new Ngo(id, name, req, pick, max, contact));
    }
}
