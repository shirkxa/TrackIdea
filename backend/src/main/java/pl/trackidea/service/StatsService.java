package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.*;

import java.time.Duration;
import java.util.*;

/** Mierniki Radaru: ile innowacji odnaleziono, jak szybko są weryfikowane i jak trafna jest propozycja oceny. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final InnovationRepository innovations;
    private final AssessmentRepository assessments;
    private final RadarRecordRepository radar;
    private final NeedRepository needs;

    public StatsService(InnovationRepository innovations, AssessmentRepository assessments,
                        RadarRecordRepository radar, NeedRepository needs) {
        this.innovations = innovations;
        this.assessments = assessments;
        this.radar = radar;
        this.needs = needs;
    }

    public record Kpi(String key, String label, String value, String hint, String target) {
    }

    /** {@code demoRecords} – ile kart w katalogu to fikcyjne przykłady; mierniki liczone na nich nie opisują prawdziwej pracy. */
    public record Stats(List<Kpi> kpis, Map<String, Long> byStatus, Map<String, Long> byArea, Map<String, Long> bySector,
                        Map<String, Long> byInstitutionType, Map<String, Long> bySource, long demoRecords,
                        long demoAssessments) {
    }

    public Stats stats() {
        List<Innovation> all = innovations.findAll();
        List<Innovation> visible = all.stream().filter(i -> i.getStatus().isPubliclyVisible()).toList();

        long verified = visible.stream().filter(i -> i.getStatus() == InnovationStatus.OCENIONA).count();
        long fromRadar = visible.stream().filter(i -> i.getSource() == Source.RADAR).count();
        long sources = all.stream().map(Innovation::getSourceName).filter(Objects::nonNull).distinct().count();

        List<Long> daysToAssessment = new ArrayList<>();
        int criteriaTotal = 0, criteriaAgree = 0;
        for (Assessment a : assessments.findAll()) {
            if (!a.isApproved()) continue;
            daysToAssessment.add(Duration.between(a.getInnovation().getCreatedAt(), a.getApprovedAt()).toDays());
            for (CriterionScore s : a.getScores()) {
                criteriaTotal++;
                if (Math.abs(s.effectiveScore() - s.getProposedScore()) <= 1) criteriaAgree++;
            }
        }
        double avgDays = daysToAssessment.stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
        long duplicates = radar.countByOutcome(RadarRecord.Outcome.DUPLIKAT)
                + all.stream().filter(i -> i.getStatus() == InnovationStatus.POLACZONA).count();
        long rejected = all.stream().filter(i -> i.getStatus() == InnovationStatus.ODRZUCONA).count();

        List<Kpi> kpis = List.of(
                new Kpi("found", "Innowacje w katalogu", String.valueOf(visible.size()),
                        "z Radaru: " + fromRadar + " · źródła: " + sources, "100+ w pierwszym kwartale"),
                new Kpi("verified", "Zweryfikowane przez PCI", visible.isEmpty() ? "–" : pct(verified, visible.size()),
                        verified + " z " + visible.size() + " rekordów", "≥ 50%"),
                new Kpi("time", "Czas: wykrycie → ocena",
                        Double.isNaN(avgDays) ? "–" : String.format(Locale.ROOT, "%.1f dni", avgDays),
                        "zatwierdzone oceny: " + daysToAssessment.size(), "≤ 14 dni"),
                new Kpi("ai", "Zgodność propozycji z analitykiem",
                        criteriaTotal == 0 ? "–" : pct(criteriaAgree, criteriaTotal),
                        "kryteria z różnicą ≤ 1 pkt: " + criteriaAgree + "/" + criteriaTotal, "≥ 80%"),
                new Kpi("duplicates", "Zatrzymane duplikaty", String.valueOf(duplicates),
                        "ten sam projekt z kilku źródeł", "mierzone od startu"),
                new Kpi("rejected", "Odrzucone rekordy", String.valueOf(rejected),
                        "nie spełniały definicji innowacji", "–"),
                new Kpi("needs", "Zapytania o rozwiązanie", String.valueOf(needs.findByOpenTrue().size()),
                        "potrzeby z dopasowanymi innowacjami", "–"));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (InnovationStatus s : InnovationStatus.values()) {
            long c = all.stream().filter(i -> i.getStatus() == s).count();
            if (c > 0) byStatus.put(s.getLabel(), c);
        }
        Map<String, Long> byArea = new LinkedHashMap<>();
        for (Area a : Area.values()) {
            long c = visible.stream().filter(i -> i.getArea() == a).count();
            if (c > 0) byArea.put(a.getLabel(), c);
        }
        Map<String, Long> bySector = new LinkedHashMap<>();
        for (Sector s : Sector.values()) {
            long c = visible.stream().filter(i -> i.getSector() == s).count();
            if (c > 0) bySector.put(s.getLabel(), c);
        }
        Map<String, Long> byInst = new LinkedHashMap<>();
        for (InstitutionType t : InstitutionType.values()) {
            long c = visible.stream().filter(i -> i.getInstitution() != null && i.getInstitution().getType() == t).count();
            if (c > 0) byInst.put(t.getLabel(), c);
        }
        Map<String, Long> bySource = new LinkedHashMap<>();
        visible.stream()
                .map(i -> i.getSourceName() != null ? i.getSourceName() : i.getSource().getLabel())
                .forEach(name -> bySource.merge(name, 1L, Long::sum));
        long demoRecords = visible.stream().filter(i -> i.getSource() == Source.ANALITYK).count();
        long demoAssessments = assessments.findAll().stream()
                .filter(a -> a.isApproved() && a.getInnovation().getSource() == Source.ANALITYK).count();
        return new Stats(kpis, byStatus, byArea, bySector, byInst, bySource, demoRecords, demoAssessments);
    }

    private static String pct(long a, long b) {
        return Math.round(100.0 * a / b) + "%";
    }
}
