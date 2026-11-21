package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.AssessmentRepository;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.VoteRepository;

import java.util.*;

/**
 * Propozycja oceny według 6 jawnych kryteriów. Reguły są proste i opisane w uzasadnieniu,
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
    private final VoteRepository votes;
    private final InnovationRepository innovations;
    private final AssessmentRepository assessments;

    public AssessmentService(SimilarityService similarity, MatchingService matching, VoteRepository votes,
                             InnovationRepository innovations, AssessmentRepository assessments) {
        this.similarity = similarity;
        this.matching = matching;
        this.votes = votes;
        this.innovations = innovations;
        this.assessments = assessments;
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
        a.getScores().add(scale(inn));
        a.getScores().add(novelty(inn));
        a.getScores().add(maturity(inn));
        a.getScores().add(transferability(inn));
        a.getScores().add(impact(inn));
        a.getScores().add(team(inn));
        return a;
    }

    private CriterionScore scale(Innovation inn) {
        List<MatchingService.Match> needs = matching.needsFor(inn);
        long strong = needs.stream().filter(m -> m.score() >= 55).count();
        long v = votes.countByInnovation(inn);
        String text = TextVectorizer.normalize(inn.getProblem() + " " + inn.getSummary());
        boolean broad = SCALE_HINTS.stream().anyMatch(text::contains);

        int score = 1 + (int) Math.min(2, strong) + (v >= 5 ? 1 : 0) + (broad ? 1 : 0);
        if (score == 1 && !needs.isEmpty()) {
            score = 2;
        }
        StringBuilder why = new StringBuilder();
        why.append("Pasujące potrzeby na tablicy: ").append(needs.size())
                .append(" (mocne dopasowanie ≥55%: ").append(strong).append(")");
        if (!needs.isEmpty()) {
            why.append(", np. „").append(needs.getFirst().title()).append("” (").append(needs.getFirst().subtitle()).append(")");
        }
        why.append(". Głosy „przydałoby się u nas”: ").append(v).append(".");
        if (broad) {
            why.append(" Opis problemu wskazuje na powszechne zjawisko.");
        }
        return new CriterionScore(Criterion.SKALA, clamp(score), why.toString());
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
        return new CriterionScore(Criterion.NOWOSC, score, why);
    }

    private CriterionScore maturity(Innovation inn) {
        Maturity m = inn.getMaturity() == null ? Maturity.POMYSL : inn.getMaturity();
        int forks = (int) innovations.countByForkedFromAndStatus(inn, InnovationStatus.WDROZONA);
        int score = Math.max(m.getLevel(), forks > 0 ? 5 : 0);
        String why = "Etap deklarowany w karcie: " + m.getLabel() + "."
                + (forks > 0 ? " Potwierdzone wdrożenia w innych instytucjach: " + forks + "." : "");
        return new CriterionScore(Criterion.DOJRZALOSC, clamp(score), why);
    }

    private CriterionScore transferability(Innovation inn) {
        InnovationType type = inn.getType() == null ? InnovationType.TECHNICZNA : inn.getType();
        int score = type.isNonTechnical() ? 4 : 2;
        List<String> why = new ArrayList<>();
        why.add("Typ " + type.getLabel().toLowerCase() + (type.isNonTechnical()
                ? " – zwykle nie wymaga specjalistycznego sprzętu" : " – wdrożenie zależy od technologii i zaplecza"));
        String req = TextVectorizer.normalize(inn.getRequirements() + " " + inn.getSolution());
        Optional<String> cheap = LOW_COST_HINTS.stream().filter(req::contains).findFirst();
        Optional<String> costly = HIGH_COST_HINTS.stream().filter(req::contains).findFirst();
        if (cheap.isPresent()) {
            score++;
            why.add("wymagania wskazują na niski koszt lub gotowe materiały");
        }
        if (costly.isPresent()) {
            score--;
            why.add("wymaga specjalistycznego zaplecza lub sprzętu");
        }
        long forks = innovations.findByForkedFrom(inn).size();
        if (forks > 0) {
            score++;
            why.add("już przeniesiona do innych instytucji (forki: " + forks + ")");
        }
        if (inn.getTransferModel() == TransferModel.OTWARTA_REPLIKACJA) {
            why.add("autor udostępnia rozwiązanie do otwartej replikacji");
        }
        return new CriterionScore(Criterion.PRZENASZALNOSC, clamp(score), String.join("; ", why) + ".");
    }

    private CriterionScore impact(Innovation inn) {
        String results = inn.getResults() == null ? "" : inn.getResults().trim();
        if (results.isEmpty()) {
            return new CriterionScore(Criterion.WPLYW, 1, "Brak opisanych efektów – warto uzupełnić sekcję „Efekty”.");
        }
        int score = 2;
        List<String> why = new ArrayList<>();
        boolean numbers = results.matches("(?s).*\\d.*");
        boolean percent = results.contains("%");
        if (numbers) {
            score++;
            why.add("efekty opisane liczbowo");
        } else {
            why.add("efekty opisane jakościowo");
        }
        if (percent) {
            score++;
            why.add("podano zmianę procentową");
        }
        String norm = TextVectorizer.normalize(results);
        if (norm.contains("nagrod") || norm.contains("wyroznien") || norm.contains("ankiet") || norm.contains("badani")) {
            score++;
            why.add("efekty potwierdzone (ankieta, badanie lub nagroda)");
        }
        return new CriterionScore(Criterion.WPLYW, clamp(score), capitalize(String.join("; ", why)) + ".");
    }

    private CriterionScore team(Innovation inn) {
        int authors = inn.getAuthors().size();
        int score = 1 + Math.min(2, authors);
        List<String> why = new ArrayList<>();
        why.add(authors == 0 ? "autor nie ma jeszcze konta w systemie" : "autorzy z kontem: " + authors);
        boolean links = !inn.getLinks().isEmpty() || inn.getAuthors().stream()
                .anyMatch(p -> p.getGithub() != null || p.getYoutube() != null || p.getLinkedin() != null);
        if (links) {
            score++;
            why.add("podpięte materiały lub profile (GitHub, YouTube, LinkedIn)");
        }
        if (inn.getInstitution() != null && inn.getInstitution().getType().isPublicSector()) {
            score++;
            why.add("zaplecze instytucji: " + inn.getInstitution().getName());
        }
        return new CriterionScore(Criterion.ZESPOL, clamp(score), capitalize(String.join("; ", why)) + ".");
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
