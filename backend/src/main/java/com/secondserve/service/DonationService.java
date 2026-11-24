package com.secondserve.service;

import com.secondserve.exception.ConflictException;
import com.secondserve.exception.NotFoundException;
import com.secondserve.exception.ValidationException;
import com.secondserve.model.Donation;
import com.secondserve.repository.DonationRepository;
import com.secondserve.repository.TravelRepository;
import com.secondserve.util.Fields;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

public final class DonationService {
    private static final Pattern ID = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");

    public record CreateResult(Donation donation, DuplicateScreen.Result screening) {}
    public record ImportRow(int line, String batchId, String status, String message) {}
    public record ImportReport(int total, int imported, int duplicatesRejected, int falsePositivesAccepted, int invalid, List<ImportRow> rows) {}

    private final DonationRepository repo;
    private final TravelRepository travel;
    private final DuplicateScreen screening;

    public DonationService(DonationRepository repo, TravelRepository travel, DuplicateScreen screening) {
        this.repo = repo; this.travel = travel; this.screening = screening;
    }

    public List<Donation> list() { return repo.findAll(); }

    public static String normalizeId(String raw) { return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT); }

    private Donation parse(Map<String, Object> body, String forcedId) {
        Fields f = new Fields(body);
        String id = forcedId;
        if (id == null) {
            id = normalizeId(f.str("batchId", "Donation batch ID", true, 40));
            if (id != null && !ID.matcher(id).matches()) { f.error("Donation batch ID must be 2-32 letters, digits, - or _ (e.g. BATCH-101)"); }
        }
        String donor = f.str("donorName", "Donor name", true, 80);
        Integer meals = f.integer("meals", "Number of meal packs", 1, 100_000);
        LocalDateTime from = f.time("availableFrom", "Availability time");
        LocalDateTime until = f.time("safeUntil", "Latest safe delivery time");
        String notes = f.str("notes", "Notes", false, 200);
        if (from != null && until != null && !until.isAfter(from)) f.error("Latest safe delivery time must be after the availability time");
        f.throwIfInvalid();
        return new Donation(id, donor, meals, from, until, notes == null ? "" : notes);
    }

    public CreateResult create(Map<String, Object> body) {
        Donation d = parse(body, null);
        DuplicateScreen.Result s = screening.screen(d.batchId());
        if (s.outcome() == DuplicateScreen.Outcome.DUPLICATE)
            throw new ConflictException("Batch ID " + d.batchId() + " already exists. The Bloom filter reported it as possibly present and the exact repository lookup confirmed the duplicate, so the submission was rejected.");
        repo.save(d);
        screening.register(d.batchId());
        return new CreateResult(d, s);
    }

    public Donation update(String rawId, Map<String, Object> body) {
        String id = normalizeId(rawId);
        if (!repo.exists(id)) throw new NotFoundException("Donation batch " + id + " does not exist");
        Donation d = parse(body, id);
        repo.save(d);
        return d;
    }

    public void delete(String rawId) {
        String id = normalizeId(rawId);
        if (!repo.delete(id)) throw new NotFoundException("Donation batch " + id + " does not exist");
        travel.removeDonation(id);
        screening.rebuild(repo.ids()); // Bloom filters cannot delete: rebuild from the authoritative store
    }

    /** CSV columns: batchId,donor,meals,availableFrom,safeUntil,notes. Times: ISO local or +N / -N minutes from now. */
    public ImportReport importCsv(String csv) {
        if (csv == null || csv.isBlank()) throw new ValidationException("CSV content is empty");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        List<ImportRow> rows = new ArrayList<>();
        int imported = 0, dup = 0, fp = 0, invalid = 0, line = 0;
        for (String raw : csv.split("\\r?\\n")) {
            line++;
            if (raw.isBlank()) continue;
            String[] c = raw.split(",", 6);
            if (line == 1 && c[0].trim().equalsIgnoreCase("batchId")) continue;
            String id = normalizeId(c[0]);
            if (c.length < 5) { invalid++; rows.add(new ImportRow(line, id, "INVALID", "Expected at least 5 comma-separated columns")); continue; }
            Map<String, Object> m = new HashMap<>();
            m.put("batchId", c[0]); m.put("donorName", c[1]); m.put("meals", c[2].trim());
            m.put("availableFrom", relTime(c[3].trim(), now)); m.put("safeUntil", relTime(c[4].trim(), now));
            m.put("notes", c.length > 5 ? c[5] : "");
            try {
                CreateResult r = create(m);
                imported++;
                if (r.screening().outcome() == DuplicateScreen.Outcome.FALSE_POSITIVE_ACCEPTED) {
                    fp++;
                    rows.add(new ImportRow(line, id, "IMPORTED", "Bloom filter false positive: exact lookup showed the ID is new, so it was accepted"));
                } else rows.add(new ImportRow(line, id, "IMPORTED", "Imported"));
            } catch (ConflictException e) { dup++; rows.add(new ImportRow(line, id, "DUPLICATE_REJECTED", "Duplicate batch ID confirmed by exact lookup")); }
            catch (ValidationException e) { invalid++; rows.add(new ImportRow(line, id, "INVALID", String.join("; ", e.getErrors()))); }
        }
        return new ImportReport(rows.size(), imported, dup, fp, invalid, rows);
    }

    private static String relTime(String s, LocalDateTime now) {
        if (s.matches("^[+-]?\\d{1,6}$")) return now.plusMinutes(Long.parseLong(s.startsWith("+") ? s.substring(1) : s)).toString();
        return s;
    }
}
