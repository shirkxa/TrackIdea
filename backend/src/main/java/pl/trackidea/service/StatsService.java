package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.*;

import java.time.Duration;
import java.util.*;

/** Mierniki skuteczności z rozdziału 11 planu – panel PCI. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final InnovationRepository innovations;
    private final AssessmentRepository assessments;
    private final ContactRequestRepository contacts;
    private final RadarRecordRepository radar;
    private final NeedRepository needs;

    public StatsService(InnovationRepository innovations, AssessmentRepository assessments, ContactRequestRepository contacts,
                        RadarRecordRepository radar, NeedRepository needs) {
        this.innovations = innovations;
        this.assessments = assessments;
        this.contacts = contacts;
        this.radar = radar;
        this.needs = needs;
    }

    public record Kpi(String key, String label, String value, String hint, String target) {
    }

    public record Stats(List<Kpi> kpis, Map<String, Long> byStatus, Map<String, Long> bySector,
                        Map<String, Long> byInstitutionType, Map<String, Long> bySource) {
    }

    public Stats stats() {
        List<Innovation> all = innovations.findAll();
        List<Innovation> originals = all.stream().filter(i -> i.getForkedFrom() == null).toList();

        long detected = originals.stream().filter(i -> i.getSource() == Source.RADAR).count();
        long reported = originals.stream().filter(i -> i.getSource() != Source.RADAR).count();
        long detectedConfirmed = originals.stream()
                .filter(i -> i.getSource() == Source.RADAR && !i.getStatus().isDraft()).count();

        List<Long> daysToAssessment = new ArrayList<>();
        for (Innovation i : originals) {
            Optional<Assessment> a = assessments.findTopByInnovationOrderByCreatedAtDescIdDesc(i);
            if (a.isPresent() && a.get().isApproved()) {
                daysToAssessment.add(Duration.between(i.getCreatedAt(), a.get().getApprovedAt()).toDays());
            }
        }
        double avgDays = daysToAssessment.stream().mapToLong(Long::longValue).average().orElse(Double.NaN);

        int criteriaTotal = 0, criteriaAgree = 0;
        for (Assessment a : assessments.findAll()) {
            if (!a.isApproved()) continue;
            for (CriterionScore s : a.getScores()) {
                criteriaTotal++;
                if (Math.abs(s.effectiveScore() - s.getProposedScore()) <= 1) criteriaAgree++;
            }
        }

        long contactsAll = contacts.count();
        long contactsAccepted = contacts.countByStatus(ContactStatus.ZAAKCEPTOWANA);
        long forks = all.stream().filter(i -> i.getForkedFrom() != null).count();
        long deployments = all.stream()
                .filter(i -> i.getForkedFrom() != null && i.getStatus() == InnovationStatus.WDROZONA).count();
        long duplicatesStopped = radar.countByOutcome(RadarRecord.Outcome.DUPLIKAT)
                + all.stream().filter(i -> i.getStatus() == InnovationStatus.POLACZONA).count() + forks;

        List<Kpi> kpis = List.of(
                new Kpi("found", "Odnalezione innowacje", String.valueOf(originals.size()),
                        "Radar: " + detected + " · zgłoszenia: " + reported, "100+ w pierwszym kwartale"),
                new Kpi("confirmed", "Wykryte potwierdzone przez autorów",
                        detected == 0 ? "–" : pct(detectedConfirmed, detected),
                        detectedConfirmed + " z " + detected + " wykrytych przez Radar", "≥ 50%"),
                new Kpi("time", "Czas: zgłoszenie → ocena",
                        Double.isNaN(avgDays) ? "–" : String.format(Locale.ROOT, "%.1f dni", avgDays),
                        "zatwierdzone oceny: " + daysToAssessment.size(), "≤ 14 dni"),
                new Kpi("ai", "Zgodność propozycji z ekspertem",
                        criteriaTotal == 0 ? "–" : pct(criteriaAgree, criteriaTotal),
                        "kryteria z różnicą ≤ 1 pkt: " + criteriaAgree + "/" + criteriaTotal, "≥ 80%"),
                new Kpi("contacts", "Kontakty zaakceptowane",
                        contactsAll == 0 ? "–" : pct(contactsAccepted, contactsAll),
                        contactsAccepted + " z " + contactsAll + " próśb o kontakt", "≥ 20% konwersji"),
                new Kpi("deployments", "Forki / potwierdzone wdrożenia", forks + " / " + deployments,
                        "transfer do innych instytucji", "10+ w pierwszym roku"),
                new Kpi("duplicates", "Zatrzymane duplikaty", String.valueOf(duplicatesStopped),
                        "duplikaty z Radaru + połączenia + forki zamiast budowy od zera", "mierzone od startu"),
                new Kpi("needs", "Otwarte potrzeby na tablicy", String.valueOf(needs.findByOpenTrue().size()),
                        "problemy zgłoszone przez instytucje i firmy", "–"));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (InnovationStatus s : InnovationStatus.values()) {
            long c = originals.stream().filter(i -> i.getStatus() == s).count();
            if (c > 0) byStatus.put(s.getLabel(), c);
        }
        Map<String, Long> bySector = new LinkedHashMap<>();
        for (Sector s : Sector.values()) {
            long c = originals.stream().filter(i -> i.getSector() == s).count();
            if (c > 0) bySector.put(s.getLabel(), c);
        }
        Map<String, Long> byInst = new LinkedHashMap<>();
        for (InstitutionType t : InstitutionType.values()) {
            long c = originals.stream().filter(i -> i.getInstitution() != null && i.getInstitution().getType() == t).count();
            if (c > 0) byInst.put(t.getLabel(), c);
        }
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (Source s : Source.values()) {
            long c = all.stream().filter(i -> i.getSource() == s).count();
            if (c > 0) bySource.put(s.getLabel(), c);
        }
        return new Stats(kpis, byStatus, bySector, byInst, bySource);
    }

    private static String pct(long a, long b) {
        return Math.round(100.0 * a / b) + "%";
    }
}
