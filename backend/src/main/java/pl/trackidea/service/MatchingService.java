package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.NeedRepository;
import pl.trackidea.repo.PersonRepository;

import java.util.*;

/**
 * Polecajki „jak na LinkedInie”, ale wytłumaczalne:
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

    private final SimilarityService similarity;
    private final NeedRepository needs;
    private final PersonRepository people;
    private final InnovationRepository innovations;

    public MatchingService(SimilarityService similarity, NeedRepository needs, PersonRepository people,
                           InnovationRepository innovations) {
        this.similarity = similarity;
        this.needs = needs;
        this.people = people;
        this.innovations = innovations;
    }

    public record Match(String kind, Long id, String title, String subtitle, int score, List<String> reasons,
                        Long contactPersonId, Long institutionId, String institutionType) {
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
            if (!inn.getStatus().isPubliclyVisible() || inn.getForkedFrom() != null
                    || sameInstitution(inn.getInstitution(), need.getInstitution())) {
                continue;
            }
            scoreNeed(idx, inn, need, true).ifPresent(out::add);
        }
        return top(out, 6);
    }

    /** Partnerzy i inwestorzy, których profil pasuje do innowacji. */
    public List<Match> partnersFor(Innovation inn) {
        SimilarityService.Index idx = similarity.index();
        List<Match> out = new ArrayList<>();
        for (Person p : people.findByRole(Role.PARTNER)) {
            scorePartner(idx, inn, p, false).ifPresent(out::add);
        }
        return top(out, 5);
    }

    /** Polecajki dla zalogowanego partnera / opiekuna. */
    public List<Match> recommendationsFor(Person person) {
        SimilarityService.Index idx = similarity.index();
        List<Match> out = new ArrayList<>();
        if (person.getRole() == Role.PARTNER) {
            for (Innovation inn : innovations.findAll()) {
                if (inn.getStatus().isPubliclyVisible() && inn.getForkedFrom() == null) {
                    scorePartner(idx, inn, person, true).ifPresent(out::add);
                }
            }
        } else if (person.getInstitution() != null) {
            for (Need need : needs.findByOpenTrue()) {
                if (sameInstitution(need.getInstitution(), person.getInstitution())) {
                    for (Match m : innovationsForNeed(need)) {
                        List<String> reasons = new ArrayList<>(m.reasons());
                        reasons.addFirst("odpowiada na Waszą potrzebę „" + need.getTitle() + "”");
                        out.add(new Match(m.kind(), m.id(), m.title(), m.subtitle(), m.score(), reasons,
                                m.contactPersonId(), m.institutionId(), m.institutionType()));
                    }
                }
            }
        }
        Map<Long, Match> unique = new LinkedHashMap<>();
        out.stream().sorted(Comparator.comparingInt(Match::score).reversed())
                .forEach(m -> unique.putIfAbsent(m.id(), m));
        return top(new ArrayList<>(unique.values()), 8);
    }

    private Optional<Match> scoreNeed(SimilarityService.Index idx, Innovation inn, Need need, boolean innovationSide) {
        double sim = idx.similarity(inn.searchText(), need.searchText());
        if (sim < 0.08) {
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
            Long contact = inn.getAuthors().stream().map(Person::getId).findFirst().orElse(null);
            return Optional.of(new Match("INNOWACJA", inn.getId(), inn.getTitle(), instName(inn.getInstitution()),
                    pct(score), reasons, contact, instId(inn.getInstitution()), instType(inn.getInstitution())));
        }
        Long contact = need.getAuthor() == null ? null : need.getAuthor().getId();
        return Optional.of(new Match("POTRZEBA", need.getId(), need.getTitle(), instName(need.getInstitution()),
                pct(score), reasons, contact, instId(need.getInstitution()), instType(need.getInstitution())));
    }

    private Optional<Match> scorePartner(SimilarityService.Index idx, Innovation inn, Person p, boolean innovationSide) {
        String profile = String.join(" ", nz(p.getHeadline()), nz(p.getBio()), String.join(" ", p.getInterestTags()),
                p.getInterestSectors().stream().map(Sector::getLabel).reduce("", (a, b) -> a + " " + b));
        double sim = idx.similarity(inn.searchText(), profile);
        List<String> reasons = new ArrayList<>();
        if (sim >= 0.10) {
            reasons.add("profil zbieżny z opisem (" + SimilarityService.percent(sim) + "%)");
        }

        double sector = 0;
        if (inn.getSector() != null && p.getInterestSectors().contains(inn.getSector())) {
            sector = 1;
            reasons.add("interesuje się sektorem " + inn.getSector().getLabel());
        }
        Set<String> common = commonTags(inn.getTags(), p.getInterestTags());
        if (!common.isEmpty()) {
            sector = 1;
            reasons.add("obserwuje tag „" + common.iterator().next() + "”");
        }

        double stage = 0.5;
        if (!p.getSeekingStages().isEmpty() && inn.getMaturity() != null) {
            if (p.getSeekingStages().contains(inn.getMaturity())) {
                stage = 1;
                reasons.add("szuka rozwiązań na etapie: " + inn.getMaturity().getLabel());
            } else {
                stage = 0.2;
            }
        }
        double region = regionScore(inn.getInstitution(), p.getInstitution(), reasons);

        double score = W_TEXT * Math.min(1, sim / TEXT_SATURATION) + W_SECTOR * sector + W_STAGE * stage + W_REGION * region;
        if (score < 0.35 || (sector == 0 && sim < 0.1)) {
            return Optional.empty();
        }
        if (innovationSide) {
            Long contact = inn.getAuthors().stream().map(Person::getId).findFirst().orElse(null);
            return Optional.of(new Match("INNOWACJA", inn.getId(), inn.getTitle(), instName(inn.getInstitution()),
                    pct(score), reasons, contact, instId(inn.getInstitution()), instType(inn.getInstitution())));
        }
        String sub = (p.getHeadline() != null ? p.getHeadline() : p.getRole().getLabel())
                + (p.getInstitution() != null ? " · " + p.getInstitution().getName() : "");
        return Optional.of(new Match("PARTNER", p.getId(), p.getName(), sub, pct(score), reasons, p.getId(),
                instId(p.getInstitution()), instType(p.getInstitution())));
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
