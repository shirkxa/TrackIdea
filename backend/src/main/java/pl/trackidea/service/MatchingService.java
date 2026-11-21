package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.NeedRepository;

import java.util.*;

/**
 * Dopasowanie innowacji do potrzeb („szukam rozwiązania dla…”), zawsze z wyjaśnieniem:
 * dopasowanie = 0,5 × podobieństwo treści + 0,2 × sektor i tagi + 0,15 × etap + 0,15 × region.
 */
@Service
@Transactional(readOnly = true)
public class MatchingService {

    static final double W_TEXT = 0.5;
    static final double W_SECTOR = 0.2;
    static final double W_STAGE = 0.15;
    static final double W_REGION = 0.15;

    /** Podobieństwo cosinusowe, przy którym uznajemy treść za „w pełni zgodną” (skalowanie do 0–1). */
    private static final double TEXT_SATURATION = 0.45;

    /** Poniżej tego podobieństwa treści potrzeba i innowacja nie są do siebie dopasowywane. */
    static final double MIN_TEXT_SIMILARITY = 0.15;

    private final SimilarityService similarity;
    private final NeedRepository needs;
    private final InnovationRepository innovations;

    public MatchingService(SimilarityService similarity, NeedRepository needs, InnovationRepository innovations) {
        this.similarity = similarity;
        this.needs = needs;
        this.innovations = innovations;
    }

    public record Match(String kind, Long id, String title, String subtitle, int score, List<String> reasons,
                        Long institutionId, String institutionType) {
    }

    /** Potrzeby z tablicy, które ta innowacja może rozwiązać. */
    public List<Match> needsFor(Innovation inn) {
        SimilarityService.Index idx = similarity.index();
        List<Match> out = new ArrayList<>();
        for (Need need : needs.findByOpenTrue()) {
            if (sameInstitution(inn.getInstitution(), need.getInstitution())) {
                continue;
            }
            Optional<Match> m = scoreNeed(idx, inn, need, false);
            m.ifPresent(out::add);
        }
        return top(out, 6);
    }

    /** Innowacje pasujące do potrzeby (widok tablicy potrzeb). */
    public List<Match> innovationsForNeed(Need need) {
        SimilarityService.Index idx = similarity.index();
        List<Match> out = new ArrayList<>();
        for (Innovation inn : innovations.findAll()) {
            if (!inn.getStatus().isPubliclyVisible()
                    || sameInstitution(inn.getInstitution(), need.getInstitution())) {
                continue;
            }
            scoreNeed(idx, inn, need, true).ifPresent(out::add);
        }
        return top(out, 6);
    }

    private Optional<Match> scoreNeed(SimilarityService.Index idx, Innovation inn, Need need, boolean innovationSide) {
        double sim = idx.similarity(inn.searchText(), need.searchText());
        // sam wspólny sektor i etap to za mało: dopasowanie musi wynikać z treści
        if (sim < MIN_TEXT_SIMILARITY) {
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("podobny problem (" + SimilarityService.percent(sim) + "%)");

        double sector = sectorScore(inn.getSector(), need.getSector(), inn.getTags(), need.getTags(), reasons);

        double stage;
        int level = inn.getMaturity() == null ? 1 : inn.getMaturity().getLevel();
        if (need.isWantsReadySolution()) {
            stage = level >= 3 ? 1.0 : 0.3;
            if (level >= 3) {
                reasons.add("etap: " + inn.getMaturity().getLabel() + " – gotowe do wdrożenia");
            }
        } else {
            stage = level <= 3 ? 1.0 : 0.6;
            reasons.add("szukają partnera do rozwoju");
        }
        double region = regionScore(inn.getInstitution(), need.getInstitution(), reasons);

        double score = W_TEXT * Math.min(1, sim / TEXT_SATURATION) + W_SECTOR * sector + W_STAGE * stage + W_REGION * region;
        if (score < 0.4) {
            return Optional.empty();
        }
        if (innovationSide) {
            return Optional.of(new Match("INNOWACJA", inn.getId(), inn.getTitle(), instName(inn.getInstitution()),
                    pct(score), reasons, instId(inn.getInstitution()), instType(inn.getInstitution())));
        }
        return Optional.of(new Match("POTRZEBA", need.getId(), need.getTitle(), instName(need.getInstitution()),
                pct(score), reasons, instId(need.getInstitution()), instType(need.getInstitution())));
    }

    private double sectorScore(Sector a, Sector b, Set<String> tagsA, Set<String> tagsB, List<String> reasons) {
        if (a != null && a == b) {
            reasons.add("ten sam sektor (" + a.getLabel() + ")");
            return 1;
        }
        Set<String> common = commonTags(tagsA, tagsB);
        if (!common.isEmpty()) {
            reasons.add("wspólne tagi: " + String.join(", ", common));
            return 0.6;
        }
        return 0;
    }

    static Set<String> commonTags(Set<String> a, Set<String> b) {
        Map<String, String> stems = new HashMap<>();
        for (String t : a) {
            stems.put(String.join(" ", TextVectorizer.tokens(t)), t);
        }
        Set<String> out = new LinkedHashSet<>();
        for (String t : b) {
            String key = String.join(" ", TextVectorizer.tokens(t));
            if (!key.isBlank() && stems.containsKey(key)) {
                out.add(stems.get(key));
            }
        }
        return out;
    }

    static double regionScore(Institution a, Institution b, List<String> reasons) {
        if (a == null || b == null) {
            return 0.3;
        }
        if (a.getCounty() != null && a.getCounty().equalsIgnoreCase(nz(b.getCounty()))) {
            reasons.add("ten sam powiat (" + a.getCounty() + ")");
            return 1;
        }
        if (a.getRegion() != null && a.getRegion().equalsIgnoreCase(nz(b.getRegion()))) {
            reasons.add("region " + a.getRegion());
            return 0.6;
        }
        return 0.2;
    }

    private static boolean sameInstitution(Institution a, Institution b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    private static List<Match> top(List<Match> list, int n) {
        list.sort(Comparator.comparingInt(Match::score).reversed());
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    private static int pct(double score) {
        return (int) Math.round(Math.min(1, score) * 100);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String instName(Institution i) {
        return i == null ? "" : i.getName();
    }

    private static Long instId(Institution i) {
        return i == null ? null : i.getId();
    }

    private static String instType(Institution i) {
        return i == null ? null : i.getType().name();
    }
}
