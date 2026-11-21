package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.AssessmentRepository;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.RadarRecordRepository;

import java.util.*;

/**
 * Propozycja oceny według 5 jawnych kryteriów z FAQ (wdrożenie, TRL, innowacyjność, potencjał, wiarygodność). Reguły są proste i opisane w uzasadnieniu,
 * żeby ekspert PCI mógł je szybko sprawdzić i poprawić.
 */
@Service
@Transactional(readOnly = true)
public class AssessmentService {

    private static final List<String> LOW_COST_HINTS = List.of(
            "niski koszt", "niskim koszt", "bezplat", "darmow", "instrukc", "scenariusz", "open source", "otwart",
            "szablon", "poradnik", "bez dodatkowych", "gotowe materialy", "przewodnik");
    private static final List<String> HIGH_COST_HINTS = List.of(
            "laborator", "specjalistycz", "certyfik", "kosztown", "drogi sprzet", "drogiego", "licencje komercyjne",
            "wymaga zespolu", "dedykowan");
    private static final List<String> SCALE_HINTS = List.of(
            "wszystkich szkol", "kazdej szkole", "kazdej gminie", "kazdym urzedzie", "powszechn", "w calej polsce",
            "wiekszosc", "tysiac", "setki", "wszystkie gminy", "kazdego roku");

    private final SimilarityService similarity;
    private final MatchingService matching;
    private final InnovationRepository innovations;
    private final AssessmentRepository assessments;
    private final RadarRecordRepository radarRecords;

    public AssessmentService(SimilarityService similarity, MatchingService matching,
                             InnovationRepository innovations, AssessmentRepository assessments,
                             RadarRecordRepository radarRecords) {
        this.similarity = similarity;
        this.matching = matching;
        this.innovations = innovations;
        this.assessments = assessments;
        this.radarRecords = radarRecords;
    }

    public Optional<Assessment> latest(Innovation inn) {
        return assessments.findTopByInnovationOrderByCreatedAtDescIdDesc(inn);
    }

    /** Zatwierdzona ocena albo – jeśli jej nie ma – świeża propozycja (bez zapisu). */
    public Assessment currentOrPreview(Innovation inn) {
        return latest(inn).orElseGet(() -> propose(inn));
    }

    public Assessment propose(Innovation inn) {
        Assessment a = new Assessment();
        a.setInnovation(inn);
        a.getScores().add(deployment(inn));
        a.getScores().add(trl(inn));
        a.getScores().add(novelty(inn));
        a.getScores().add(potential(inn));
        a.getScores().add(credibility(inn));
        return a;
    }

    private CriterionScore deployment(Innovation inn) {
        Maturity m = inn.getMaturity() == null ? Maturity.POMYSL : inn.getMaturity();
        String why = "Etap wdrożenia: " + m.getLabel() + estimateNote(inn, "maturity") + ".";
        return new CriterionScore(Criterion.WDROZENIE, clamp(m.getLevel()), why);
    }

    private CriterionScore trl(Innovation inn) {
        int level = trlOf(inn);
        String why = "TRL " + level + " – " + Trl.label(level).toLowerCase() + estimateNote(inn, "trl") + ".";
        return new CriterionScore(Criterion.TRL, Trl.toScore(level), why);
    }

    private CriterionScore novelty(Innovation inn) {
        List<SimilarItem> similarInn = similarity.similarInnovations(inn.searchText(), inn.getId(), 3);
        List<SimilarItem> prior = similarity.similarPriorArt(inn.searchText(), 3);
        List<SimilarItem> all = new ArrayList<>(similarInn);
        all.addAll(prior);
        all.sort(Comparator.comparingInt(SimilarItem::similarityPercent).reversed());
        int max = all.isEmpty() ? 0 : all.getFirst().similarityPercent();

        int score = max >= 75 ? 1 : max >= 55 ? 2 : max >= 38 ? 3 : max >= 25 ? 4 : 5;
        String why;
        if (all.isEmpty() || max < 25) {
            why = "Nie znaleziono istotnie podobnych rozwiązań w bazie innowacji, patentów i publikacji.";
        } else {
            StringBuilder sb = new StringBuilder("Najbardziej podobne: ");
            all.stream().filter(s -> s.similarityPercent() >= 25).limit(3).forEach(s -> sb
                    .append(kindLabel(s.kind())).append(" „").append(s.title()).append("” (")
                    .append(s.similarityPercent()).append("%); "));
            long patents = prior.stream().filter(p -> p.similarityPercent() >= 30 && "PATENT".equals(p.kind())).count();
            if (patents > 0) {
                sb.append("Podobne patenty: ").append(patents).append(".");
            }
            why = sb.toString().trim();
        }
        return new CriterionScore(Criterion.INNOWACYJNOSC, score, why);
    }

    /** Potencjał = skala (ile instytucji ma podobną potrzebę) + przenaszalność (koszt i warunki wdrożenia). */
    private CriterionScore potential(Innovation inn) {
        List<String> why = new ArrayList<>();

        List<MatchingService.Match> needs = matching.needsFor(inn);
        long strong = needs.stream().filter(m -> m.score() >= 55).count();
        String problem = TextVectorizer.normalize(inn.getProblem() + " " + inn.getSummary());
        boolean broad = SCALE_HINTS.stream().anyMatch(problem::contains);
        int scale = 1 + (int) Math.min(3, strong) + (broad ? 1 : 0);
        if (scale == 1 && !needs.isEmpty()) {
            scale = 2;
        }
        why.add("zapytania „szukam rozwiązania” pasujące do innowacji: " + needs.size()
                + " (mocne dopasowanie ≥55%: " + strong + ")");
        if (broad) {
            why.add("opis wskazuje na powszechny problem");
        }

        InnovationType type = inn.getType() == null ? InnovationType.TECHNICZNA : inn.getType();
        int transfer = type.isNonTechnical() ? 4 : 2;
        why.add("typ " + type.getLabel().toLowerCase() + (type.isNonTechnical()
                ? " – zwykle bez specjalistycznego sprzętu" : " – wdrożenie zależy od technologii i zaplecza"));
        String req = TextVectorizer.normalize(inn.getRequirements() + " " + inn.getSolution());
        if (LOW_COST_HINTS.stream().anyMatch(req::contains)) {
            transfer++;
            why.add("niski koszt lub gotowe materiały");
        }
        if (HIGH_COST_HINTS.stream().anyMatch(req::contains)) {
            transfer--;
            why.add("wymaga specjalistycznego zaplecza");
        }
        if (inn.getTransferModel() == TransferModel.OTWARTA_REPLIKACJA) {
            transfer++;
            why.add("rozwiązanie udostępnione do otwartej replikacji");
        }
        int score = (int) Math.round((clamp(scale) + clamp(transfer)) / 2.0);
        return new CriterionScore(Criterion.POTENCJAL, clamp(score), capitalize(String.join("; ", why)) + ".");
    }

    /** Wiarygodność: link do źródła, fakty podane przez źródło, potwierdzenie w innych bazach, zmierzone efekty. */
    private CriterionScore credibility(Innovation inn) {
        int score = 1;
        List<String> why = new ArrayList<>();
        if (!inn.getLinks().isEmpty()) {
            score++;
            why.add("jest link do źródła" + (inn.getSourceName() != null ? " (" + inn.getSourceName() + ")" : ""));
        } else {
            why.add("brak linku do źródła");
        }
        if (!inn.getSourceFacts().isEmpty() || (inn.getSourceAuthors() != null && !inn.getSourceAuthors().isBlank())) {
            score++;
            why.add("źródło podaje autorów lub dane projektu");
        }
        List<RadarRecord> confirmations = radarRecords.findByDuplicateOf(inn);
        if (!confirmations.isEmpty()) {
            score++;
            why.add("potwierdzone w innych źródłach: " + confirmations.stream().map(RadarRecord::getSource)
                    .distinct().reduce((x, y) -> x + ", " + y).orElse(""));
        }
        String results = inn.getResults() == null ? "" : inn.getResults();
        String norm = TextVectorizer.normalize(results);
        if (results.matches("(?s).*\\d.*")) {
            score++;
            why.add("efekty opisane liczbowo");
        } else if (!results.isBlank()) {
            why.add("efekty opisane tylko jakościowo");
        }
        if (norm.contains("nagrod") || norm.contains("ankiet") || norm.contains("badani") || norm.contains("ewaluac")) {
            score++;
            why.add("efekty potwierdzone ankietą, badaniem lub nagrodą");
        }
        return new CriterionScore(Criterion.WIARYGODNOSC, clamp(score), capitalize(String.join("; ", why)) + ".");
    }

    public static int trlOf(Innovation inn) {
        return inn.getTrl() != null ? inn.getTrl()
                : Trl.estimate(inn.getMaturity(), TextVectorizer.normalize(inn.getSolution() + " " + inn.getResults()));
    }

    private static String estimateNote(Innovation inn, String field) {
        return inn.getEstimated().contains(field) || ("trl".equals(field) && inn.getTrl() == null)
                ? " (szacunek automatyczny – do potwierdzenia)" : "";
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "PATENT" -> "patent";
            case "PUBLIKACJA" -> "publikacja";
            default -> "innowacja";
        };
    }

    private static int clamp(int s) {
        return Math.max(1, Math.min(5, s));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
