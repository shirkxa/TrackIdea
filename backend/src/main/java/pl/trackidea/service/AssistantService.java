package pl.trackidea.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Asystent wypełniania karty innowacji.
 * <ul>
 *   <li>Gdy ustawiono ANTHROPIC_API_KEY – pola wyodrębnia model Claude (structured output).</li>
 *   <li>W przeciwnym razie (albo przy błędzie) – reguły słownikowe, działające offline.</li>
 * </ul>
 * Asystent tylko proponuje: użytkownik zawsze może poprawić każde pole.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    public record Draft(String title, String summary, String problem, String solution, String requirements,
                        String results, InnovationType type, Sector sector, Maturity maturity, List<String> tags,
                        String engine, List<String> notes) {
    }

    /** Schemat odpowiedzi dla modelu (wyprowadzany automatycznie przez SDK). */
    record Extraction(
            @JsonPropertyDescription("Krótki, konkretny tytuł innowacji (do 80 znaków)") String title,
            @JsonPropertyDescription("Streszczenie w 1–2 zdaniach (do 300 znaków)") String summary,
            @JsonPropertyDescription("Jaki problem rozwiązuje i kogo dotyczy; pusty, jeśli tekst o tym nie mówi") String problem,
            @JsonPropertyDescription("Na czym polega rozwiązanie i co jest w nim nowego") String solution,
            @JsonPropertyDescription("Czego wymaga wdrożenie: ludzie, sprzęt, koszt; pusty, jeśli brak informacji") String requirements,
            @JsonPropertyDescription("Opisane efekty i wyniki, najlepiej liczbowe; pusty, jeśli brak") String results,
            InnovationType type,
            Sector sector,
            Maturity maturity,
            @JsonPropertyDescription("3–6 krótkich tagów, małymi literami, po polsku") List<String> tags) {
    }

    private static final String SYSTEM_PROMPT = """
            Jesteś asystentem platformy TrackIdea, która pomaga odnajdywać innowacje powstające w szkołach, \
            na uczelniach, w samorządach i innych instytucjach publicznych. Z opisu podanego przez użytkownika \
            wyodrębnij pola karty innowacji. Pisz po polsku, rzeczowo, w trzeciej osobie. \
            Używaj wyłącznie informacji z opisu: jeśli czegoś w nim nie ma, zostaw pole puste zamiast zgadywać. \
            Typ, sektor i etap dojrzałości wybierz z dozwolonych wartości, kierując się treścią opisu.""";

    private final String model;
    private final AnthropicClient client;

    public AssistantService(@Value("${trackidea.ai.model:claude-opus-5-5}") String model,
                            @Value("${trackidea.ai.enabled:true}") boolean enabled) {
        this.model = model;
        boolean hasKey = notBlank(System.getenv("ANTHROPIC_API_KEY")) || notBlank(System.getenv("ANTHROPIC_AUTH_TOKEN"));
        this.client = enabled && hasKey ? AnthropicOkHttpClient.fromEnv() : null;
        log.info("Asystent karty innowacji: {}", client != null ? "Claude (" + model + ")" : "reguły offline");
    }

    public boolean isAiEnabled() {
        return client != null;
    }

    public Draft draft(String text) {
        if (text == null || text.isBlank()) {
            throw DomainException.badRequest("Wpisz opis pomysłu własnymi słowami.");
        }
        if (client != null) {
            try {
                return draftWithClaude(text);
            } catch (RuntimeException e) {
                log.warn("Asystent AI niedostępny, używam reguł: {}", e.getMessage());
                Draft d = heuristic(text);
                List<String> notes = new ArrayList<>(d.notes());
                notes.addFirst("Model AI był niedostępny – użyto reguł offline.");
                return withNotes(d, notes);
            }
        }
        return heuristic(text);
    }

    private Draft draftWithClaude(String text) {
        StructuredMessageCreateParams<Extraction> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(Extraction.class)
                // przy odmowie modelu serwer sam ponawia zapytanie na modelu zapasowym
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage("Opis innowacji:\n\n" + text)
                .build();
        StructuredMessage<Extraction> response = client.messages().create(params);
        if (response.stopReason().map(r -> r.equals(StopReason.REFUSAL)).orElse(false)) {
            throw new IllegalStateException("model odmówił odpowiedzi");
        }
        Extraction e = response.content().stream()
                .flatMap(cb -> cb.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("pusta odpowiedź modelu"));
        List<String> notes = new ArrayList<>();
        notes.add("Pola zaproponował model " + model + " na podstawie Twojego opisu – sprawdź je przed zapisaniem.");
        return new Draft(e.title(), e.summary(), e.problem(), e.solution(), e.requirements(), e.results(),
                e.type(), e.sector(), e.maturity(), e.tags() == null ? List.of() : e.tags(), "CLAUDE", notes);
    }

    // ------------------------------------------------------------------ reguły offline

    private static final Map<InnovationType, List<String>> TYPE_KEYWORDS = Map.of(
            InnovationType.TECHNICZNA, List.of("dron", "czujnik", "sensor", "aplikac", "urzadzen", "algorytm", "prototyp",
                    "oprogramow", "robot", "sztuczn", "uczenie maszyn", "elektron", "drukark", "platform", "system",
                    "prototype", "device", "sensor", "algorithm", "software", "machine learning", "drone"),
            InnovationType.EDUKACYJNA, List.of("uczni", "lekcj", "nauczyc", "szkol", "eduk", "zajec", "kurs", "student",
                    "dydakty", "mentor", "klas", "teaching", "student", "school", "learning", "pupil", "curriculum"),
            InnovationType.ORGANIZACYJNA, List.of("organizac", "harmonogram", "zarzadz", "procedur", "zespol", "koordyn",
                    "grafik", "dyzur"),
            InnovationType.SPOLECZNA, List.of("spolecz", "senior", "mieszkanc", "wolontar", "integrac", "wykluczen",
                    "sasiedz", "rodzin"),
            InnovationType.PROCESOWA, List.of("proces", "obieg", "dokument", "wniosk", "urzed", "cyfryzac", "kolejk",
                    "e-usług", "zgloszen", "formular", "public administration", "municipal", "e-government",
                    "local government", "public service"));

    private static final Map<Sector, List<String>> SECTOR_KEYWORDS = new EnumMap<>(Map.ofEntries(
            Map.entry(Sector.EDUKACJA, List.of("szkol", "uczni", "nauczyc", "eduk", "lekcj", "student", "teaching",
                    "school", "education", "learning")),
            Map.entry(Sector.ADMINISTRACJA, List.of("urzed", "gmin", "samorzad", "wniosk", "mieszkanc", "e-usług",
                    "municipal", "public administration", "government", "citizen")),
            Map.entry(Sector.ICT, List.of("aplikac", "oprogramow", "platform", "internet", "dane", "cyfrow", "system",
                    "software", "data", "digital", "algorithm", "network")),
            Map.entry(Sector.LOTNICTWO, List.of("dron", "lotnic", "samolot", "bezzalog")),
            Map.entry(Sector.KOSMOS, List.of("satelit", "kosmic", "orbit")),
            Map.entry(Sector.MOTORYZACJA, List.of("samochod", "pojazd", "motoryz", "elektromobil")),
            Map.entry(Sector.ROLNICTWO, List.of("upraw", "rolni", "gospodarstw", "pole", "plony", "crop", "agricultur", "farm")),
            Map.entry(Sector.ENERGETYKA, List.of("energi", "fotowolt", "ogrzew", "prad", "energy", "photovoltaic", "battery")),
            Map.entry(Sector.ZDROWIE, List.of("zdrow", "pacjent", "medycz", "szpital", "lekarz", "patient", "clinical", "health")),
            Map.entry(Sector.SRODOWISKO, List.of("srodowisk", "odpad", "recykl", "smog", "powietrz", "klimat", "environment",
                    "waste", "recycling", "climate", "pollution")),
            Map.entry(Sector.JAKOSC_ZYCIA, List.of("senior", "jakosc zycia", "mieszkanc", "sport", "rekreac"))));

    private static final List<String> PROBLEM_HINTS = List.of("problem", "brak", "trudno", "klopot", "wyzwani",
            "nie maja", "nie bylo", "zbyt", "musial", "czeka", "trac", "challenge", "lack", "difficult", "issue",
            "limitation");
    private static final List<String> SOLUTION_HINTS = List.of("stworzy", "opracow", "program", "aplikac", "system",
            "rozwiazani", "polega", "wprowadzi", "zbudowa", "platform", "narzedzi", "metod", "dzieki", "we propose",
            "we developed", "approach", "solution", "method");
    private static final List<String> RESULT_HINTS = List.of("efekt", "wynik", "spadl", "spadla", "wzrosl", "wzrosla",
            "zmniejsz", "zwieksz", "popraw", "skroci", "oszczed", "ankiet", "result", "improved", "reduced",
            "increased", "accuracy");
    private static final List<String> REQUIREMENT_HINTS = List.of("wymaga", "potrzebn", "koszt", "sprzet", "wystarcz",
            "materialy", "godzin");

    private static final Set<String> GENERIC_WORDS = Set.of("program", "programu", "pierwszy", "pierwszych",
            "uczniow", "uczniowie", "szkole", "naszym", "naszej", "ktorym", "ktore", "wymaga", "tylko", "dzieki",
            "bardzo", "liczba", "przez", "spadla", "wzrosla", "rozwiazanie", "projekt", "projektu", "system");

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    public Draft heuristic(String text) {
        String clean = text.trim().replaceAll("[ \\t]+", " ");
        List<String> lines = clean.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        String firstLine = lines.isEmpty() ? clean : lines.getFirst();
        boolean firstLineIsTitle = lines.size() > 1 && firstLine.length() <= 90 && !firstLine.endsWith(".");
        String body = firstLineIsTitle ? String.join(" ", lines.subList(1, lines.size())) : String.join(" ", lines);

        List<String> sentences = new ArrayList<>(Arrays.asList(SENTENCE_SPLIT.split(body)));
        sentences.removeIf(String::isBlank);
        if (sentences.isEmpty()) {
            sentences.add(body);
        }
        String title = firstLineIsTitle ? firstLine : shorten(sentences.getFirst(), 80);
        String summary = shorten(String.join(" ", sentences.subList(0, Math.min(2, sentences.size()))), 300);

        List<String> problemSentences = sentences.stream().filter(x -> matches(x, PROBLEM_HINTS)).toList();
        List<String> resultSentences = sentences.stream()
                .filter(x -> isResult(x) && !problemSentences.contains(x)).toList();
        List<String> requirementSentences = sentences.stream()
                .filter(x -> !resultSentences.contains(x) && !problemSentences.contains(x)
                        && matches(x, REQUIREMENT_HINTS)).toList();
        List<String> solutionSentences = sentences.stream()
                .filter(x -> !resultSentences.contains(x) && !problemSentences.contains(x)
                        && !requirementSentences.contains(x)).toList();

        String norm = TextVectorizer.normalize(clean);
        InnovationType type = best(TYPE_KEYWORDS, norm).orElse(null);
        Sector sector = best(SECTOR_KEYWORDS, norm).orElse(null);
        Maturity maturity = maturity(norm);
        String problem = join(problemSentences);
        String results = join(resultSentences);

        List<String> notes = new ArrayList<>();
        notes.add("Pola zaproponowały reguły słownikowe (tryb offline) – sprawdź je przed zapisaniem.");
        if (problem.isEmpty()) notes.add("Nie znaleziono opisu problemu – dopisz, czego dotyczy i kogo.");
        if (results.isEmpty()) notes.add("Brak opisanych efektów – liczby (np. „o 30% mniej…”) podnoszą ocenę wpływu.");
        return new Draft(title, summary, problem, join(solutionSentences), join(requirementSentences), results,
                type, sector, maturity, tags(clean), "REGUŁY", notes);
    }

    private static boolean matches(String sentence, List<String> hints) {
        String n = TextVectorizer.normalize(sentence);
        return hints.stream().anyMatch(n::contains);
    }

    private static boolean isResult(String sentence) {
        boolean measured = sentence.contains("%") || sentence.matches(".*\\d+\\s*(proc|zł|godzin|dni|minut).*");
        return measured || (matches(sentence, RESULT_HINTS) && sentence.matches(".*\\d.*"));
    }

    private static String join(List<String> sentences) {
        StringBuilder sb = new StringBuilder();
        for (String x : sentences) {
            if (sb.length() + x.length() > 900) break;
            sb.append(x.trim()).append(' ');
        }
        return sb.toString().trim();
    }

    private static <T> Optional<T> best(Map<T, List<String>> keywords, String norm) {
        T best = null;
        int bestHits = 0;
        for (Map.Entry<T, List<String>> e : keywords.entrySet()) {
            int hits = 0;
            for (String k : e.getValue()) {
                hits += countOccurrences(norm, TextVectorizer.normalize(k));
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = e.getKey();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Maturity maturity(String n) {
        if (n.matches("(?s).*(w kilku|w wielu|w \\d+ (szkol|gmin|urzed|placow|instytuc)|widely used|several institutions).*")) return Maturity.DZIALA_W_WIELU;
        if (n.matches("(?s).*(dziala od|od roku|od \\d+ lat|stosujemy|wdrozon|funkcjonuje|prowadzimy od).*")) return Maturity.DZIALA_W_JEDNYM;
        if (n.contains("deployed") || n.contains("implemented in") || n.contains("in operation")) return Maturity.DZIALA_W_JEDNYM;
        if (n.contains("pilotaz") || n.contains("testuj") || n.contains("testow") || n.contains("pilot")
                || n.contains("field test") || n.contains("case study")) return Maturity.PILOTAZ;
        if (n.contains("prototyp") || n.contains("model") || n.contains("prototype")) return Maturity.PROTOTYP;
        return Maturity.POMYSL;
    }

    private static List<String> tags(String text) {
        Map<String, Integer> freq = new HashMap<>();
        Map<String, String> original = new HashMap<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9-]+")) {
            if (w.length() < 5 || GENERIC_WORDS.contains(TextVectorizer.normalize(w))) continue;
            List<String> t = TextVectorizer.tokens(w);
            if (t.isEmpty()) continue;
            String stem = t.getFirst();
            freq.merge(stem, 1, Integer::sum);
            original.merge(stem, w, (a, b) -> a.length() <= b.length() ? a : b);
        }
        return freq.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5).map(e -> original.get(e.getKey())).toList();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static String shorten(String s, int max) {
        String t = s.trim();
        if (t.length() <= max) return t.replaceAll("[.]$", "");
        int cut = t.lastIndexOf(' ', max);
        return t.substring(0, cut > 20 ? cut : max) + "…";
    }

    private static Draft withNotes(Draft d, List<String> notes) {
        return new Draft(d.title(), d.summary(), d.problem(), d.solution(), d.requirements(), d.results(), d.type(),
                d.sector(), d.maturity(), d.tags(), d.engine(), notes);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
