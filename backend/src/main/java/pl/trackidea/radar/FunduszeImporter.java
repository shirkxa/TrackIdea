package pl.trackidea.radar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pl.trackidea.domain.Area;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lista projektów Funduszy Europejskich 2021–2027 (dane.gov.pl, Ministerstwo Funduszy i Polityki Regionalnej).
 * Obejmuje też projekty PARP i NCBR (program FENG) oraz programy regionalne, m.in. Fundusze Europejskie dla Podkarpacia.
 * Z ~36 tys. projektów wybieramy innowacje z trzech obszarów (kategoria wsparcia + słowa w tytule);
 * projekty z Podkarpacia i zakończone (czyli wdrożone) są brane w pierwszej kolejności.
 */
@Component
public class FunduszeImporter implements SourceImporter {

    static final String DATASET = "https://dane.gov.pl/pl/dataset/13939";
    private static final String RESOURCES_API = "https://api.dane.gov.pl/1.4/datasets/13939/resources?per_page=1&sort=-data_date";

    // kolumny pliku: A nazwa, B opis, C nr umowy, D beneficjent, I program, K działanie, L wartość,
    // N dofinansowanie UE, P miejsce realizacji, Q początek, R koniec, S kategoria wsparcia
    private static final Set<String> COLUMNS = Set.of("A", "B", "C", "D", "I", "K", "L", "N", "P", "Q", "R", "S");

    /** Grupa projektów: kody kategorii wsparcia + słowa w tytule świadczące o innowacji. */
    record Query(String name, List<String> codes, Pattern titleWords, InnovationType type, Sector sector, Area area) {
    }

    static final List<Query> QUERIES = List.of(
            new Query("administracja", List.of("016"),
                    Pattern.compile("e-?\\s?usług|platform|cyfrow|system|aplikac|elektroniczn", Pattern.CASE_INSENSITIVE),
                    InnovationType.PROCESOWA, Sector.ADMINISTRACJA, Area.ADMINISTRACYJNY),
            new Query("edukacja", List.of("149", "150", "151"),
                    // samo „nowoczesna szkoła” to zwykle zajęcia dodatkowe i doposażenie, a nie nowa metoda
                    Pattern.compile("innowac|nowatorsk|cyfrow|STEA?M|robotyk|programowan|laborator|dualn", Pattern.CASE_INSENSITIVE),
                    InnovationType.EDUKACYJNA, Sector.EDUKACJA, Area.EDUKACYJNY),
            new Query("badania", List.of("002", "004", "010", "012", "029"),
                    Pattern.compile("B\\+R|badawcz|innowac|prototyp|technologi", Pattern.CASE_INSENSITIVE),
                    InnovationType.TECHNICZNA, null, Area.TECHNOLOGICZNY),
            new Query("wdrożenia", List.of("027", "028"),
                    Pattern.compile("innowac|wdrożen|technologi|automatyz|cyfrow", Pattern.CASE_INSENSITIVE),
                    InnovationType.TECHNICZNA, null, Area.TECHNOLOGICZNY));

    private static final Pattern LOCATION = Pattern.compile("WOJ\\.:\\s*([^,|]+)(?:,\\s*POW\\.:\\s*([^,|]+))?(?:,\\s*GM\\.:\\s*([^,|]+))?");
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private final HttpJson http;
    private final ApplicationEventPublisher events;
    private final int perQuery;
    private final Path cacheDir;
    private volatile Thread download;

    public FunduszeImporter(HttpJson http, ApplicationEventPublisher events,
                            @Value("${trackidea.radar.fe.per-query:5}") int perQuery,
                            @Value("${trackidea.radar.fe.cache-dir:${java.io.tmpdir}}") String cacheDir) {
        this.http = http;
        this.events = events;
        this.perQuery = perQuery;
        this.cacheDir = Path.of(cacheDir);
    }

    @Override
    public String name() {
        return "Fundusze Europejskie";
    }

    @Override
    public String description() {
        return "Projekty z Funduszy Europejskich 2021–2027 (w tym PARP i NCBR): innowacje firm, e-usługi urzędów, nowoczesna edukacja";
    }

    @Override
    public String homepage() {
        return DATASET;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public List<SourceRecord> fetch() throws Exception {
        Path file = latestFile();
        List<Map<String, XlsxStream.Cell>> rows = XlsxStream.rows(file, COLUMNS);

        // 1. przebieg: tylko krótkie teksty potrzebne do wyboru (tytuł, kategoria, miejsce)
        Map<Integer, String> small = XlsxStream.sharedStrings(file, sharedIndexes(rows, List.of("A", "S", "P")));
        LocalDate today = LocalDate.now();
        List<Map<String, XlsxStream.Cell>> chosen = new ArrayList<>();
        Map<Map<String, XlsxStream.Cell>, Query> queryOf = new IdentityHashMap<>();
        for (Query q : QUERIES) {
            rows.stream()
                    .filter(r -> matches(q, text(r, "S", small), text(r, "A", small)))
                    .sorted(Comparator
                            .comparing((Map<String, XlsxStream.Cell> r) -> !text(r, "P", small).toUpperCase(Locale.ROOT).contains("PODKARPACKIE"))
                            .thenComparing(r -> !finished(date(r, "R"), today))
                            .thenComparing(r -> date(r, "R"), Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(perQuery)
                    .forEach(r -> {
                        chosen.add(r);
                        queryOf.put(r, q);
                    });
        }

        // 2. przebieg: pełne teksty tylko dla wybranych projektów
        Map<Integer, String> all = new HashMap<>(small);
        all.putAll(XlsxStream.sharedStrings(file, sharedIndexes(chosen, List.of("B", "C", "D", "I", "K"))));
        List<SourceRecord> out = new ArrayList<>();
        for (Map<String, XlsxStream.Cell> r : chosen) {
            out.add(toRecord(r, queryOf.get(r), all, today));
        }
        return out;
    }

    /**
     * Najnowszy plik z dane.gov.pl (wydania miesięczne, ok. 40 MB); pobierany raz na wersję, potem czytany z dysku.
     * Pobieranie idzie w tle i jest wznawiane – wolny serwer nie blokuje pozostałych źródeł Radaru.
     */
    private Path latestFile() throws Exception {
        JsonNode res;
        try {
            res = http.get(RESOURCES_API).path("data").path(0);
        } catch (java.io.IOException e) {
            // dane.gov.pl bywa niedostępne – wtedy ostatnio pobrany plik (dane sprzed najwyżej miesiąca)
            Optional<Path> cached = newestCached();
            if (cached.isPresent()) {
                return cached.get();
            }
            throw e;
        }
        String id = res.path("id").asString("");
        String url = res.path("attributes").path("download_url").asString(
                res.path("attributes").path("file_url").asString(""));
        long size = res.path("attributes").path("file_size").asLong(0);
        if (id.isBlank() || url.isBlank()) {
            throw new IllegalStateException("dane.gov.pl nie zwróciło pliku z listą projektów");
        }
        Path file = cacheDir.resolve("trackidea-fundusze-" + id.replaceAll("\\W", "") + ".xlsx");
        if (Files.exists(file)) {
            return file;
        }
        startDownload(url, file);
        Path part = HttpJson.partOf(file);
        long have = Files.exists(part) ? Files.size(part) : 0;
        throw new SourceNotReady("Pobieranie listy projektów z dane.gov.pl: " + have / 1_000_000 + " z "
                + (size > 0 ? size / 1_000_000 + " MB" : "? MB") + " – skan ruszy sam po zakończeniu.");
    }

    private Optional<Path> newestCached() throws java.io.IOException {
        try (var files = Files.list(cacheDir)) {
            return files.filter(f -> f.getFileName().toString().matches("trackidea-fundusze-\\w+\\.xlsx"))
                    .max(Comparator.comparing(f -> f.toFile().lastModified()));
        }
    }

    private synchronized void startDownload(String url, Path file) {
        if (download != null && download.isAlive()) {
            return;
        }
        download = Thread.ofVirtual().name("fundusze-download").start(() -> {
            for (int attempt = 1; attempt <= 5 && !Files.exists(file); attempt++) {
                try {
                    http.download(url, file, 3600);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    LoggerHolder.LOG.warn("Fundusze Europejskie: pobieranie przerwane ({}), próba {} z 5", e.getMessage(), attempt);
                }
            }
            if (Files.exists(file)) {
                LoggerHolder.LOG.info("Fundusze Europejskie: plik pobrany, uruchamiam skan");
                events.publishEvent(new SourceReadyEvent(name()));
            }
        });
    }

    private static final class LoggerHolder {
        static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FunduszeImporter.class);
    }

    private SourceRecord toRecord(Map<String, XlsxStream.Cell> r, Query q, Map<Integer, String> s, LocalDate today) {
        String title = text(r, "A", s).trim();
        String contract = text(r, "C", s).trim();
        Location loc = location(text(r, "P", s));
        String raw = text(r, "D", s);
        // puste pole = beneficjent niejawny w źródle (zwykle osoba fizyczna prowadząca działalność)
        String beneficiary = raw.isBlank()
                ? "Przedsiębiorca – nazwa nieujawniona (woj. " + loc.region().toLowerCase(Locale.ROOT) + ")"
                : beneficiary(raw);
        LocalDate start = date(r, "Q");
        LocalDate end = date(r, "R");
        boolean done = finished(end, today);

        List<String> facts = new ArrayList<>();
        facts.add("Program: " + text(r, "I", s));
        String measure = text(r, "K", s);
        if (!measure.isBlank()) facts.add("Działanie: " + measure);
        facts.add("Kategoria wsparcia: " + text(r, "S", s).replaceFirst("^\\S+\\s+", ""));
        facts.add("Numer umowy: " + contract);
        if (raw.isBlank()) facts.add("Źródło nie podaje nazwy beneficjenta");
        // źródło podaje tylko daty; to, czy projekt się zakończył, Radar wylicza sam i pokazuje jako szacunek etapu
        if (start != null) facts.add("Okres realizacji według umowy: " + start + (end == null ? "" : " – " + end));
        String value = number(r, "L");
        if (value != null) facts.add("Wartość projektu: " + value + " zł");
        String eu = number(r, "N");
        if (eu != null) facts.add("Dofinansowanie UE: " + eu + " zł");
        if (loc.place() != null) facts.add("Miejsce realizacji: " + loc.place());

        return new SourceRecord(name(), contract, Names.shorten(title, 240), Names.shorten(text(r, "B", s), 2500),
                beneficiary, institutionType(beneficiary, q), loc.city(), loc.county(), loc.region(), null,
                start == null ? null : start.getYear(), List.of(), DATASET, q.type(), q.sector(),
                maturity(q, done), facts, q.area());
    }

    static boolean matches(Query q, String category, String title) {
        return q.codes().stream().anyMatch(category::startsWith) && q.titleWords().matcher(title).find();
    }

    /** Szacunek etapu: prace B+R kończą się testami, wdrożenia w urzędach, szkołach i firmach – stosowaniem w praktyce. */
    static Maturity maturity(Query q, boolean finished) {
        if ("badania".equals(q.name())) {
            return finished ? Maturity.TESTY : Maturity.PROTOTYP;
        }
        return finished ? Maturity.STOSOWANE : Maturity.PILOTAZ;
    }

    /** „Gmina Złoty Stok/Szkoła Podstawowa w Złotym Stoku” → szkoła (realizator), nazwy wielkimi literami → zwykłe. */
    static String beneficiary(String raw) {
        String name = raw == null ? "" : raw.trim();
        int slash = name.indexOf('/');
        if (slash > 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1).trim();
        }
        name = name.replaceAll("(?i)\\s*SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ", " SP. Z O.O.")
                .replaceAll("(?i)\\s*SPÓŁKA AKCYJNA", " S.A.");
        return Names.titleCase(name);
    }

    static InstitutionType institutionType(String name, Query q) {
        return Names.institutionType(name, "administracja".equals(q.name()) ? InstitutionType.INSTYTUCJA_PUBLICZNA
                : "edukacja".equals(q.name()) ? InstitutionType.SZKOLA : InstitutionType.FIRMA);
    }

    record Location(String region, String county, String city, String place) {
    }

    /** „WOJ.: PODKARPACKIE, POW.: rzeszowski, GM.: Tyczyn | …” → pierwsze miejsce realizacji. */
    static Location location(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Location("Polska", null, null, null);
        }
        Matcher m = LOCATION.matcher(raw);
        if (!m.find()) {
            return new Location("Polska", null, null, raw.trim());
        }
        String region = Names.titleCase(m.group(1).trim());
        String county = m.group(2) == null ? null : m.group(2).trim();
        String city = m.group(3) == null ? null : m.group(3).trim();
        String place = String.join(", ", Arrays.stream(new String[]{city, county == null ? null : "pow. " + county,
                "woj. " + region.toLowerCase(Locale.ROOT)}).filter(Objects::nonNull).toList());
        long more = raw.chars().filter(c -> c == '|').count();
        return new Location(region, county, city, place + (more > 0 ? " i " + more + " inne" : ""));
    }

    /** Daty w XLSX to liczba dni od 30.12.1899. */
    static LocalDate excelDate(String raw) {
        try {
            return EXCEL_EPOCH.plusDays((long) Math.floor(Double.parseDouble(raw)));
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private static LocalDate date(Map<String, XlsxStream.Cell> r, String col) {
        XlsxStream.Cell c = r.get(col);
        return c == null || c.shared() ? null : excelDate(c.raw());
    }

    private static boolean finished(LocalDate end, LocalDate today) {
        return end != null && end.isBefore(today);
    }

    private static String number(Map<String, XlsxStream.Cell> r, String col) {
        XlsxStream.Cell c = r.get(col);
        if (c == null || c.shared()) return null;
        try {
            return String.format(Locale.ROOT, "%,d", Math.round(Double.parseDouble(c.raw()))).replace(',', ' ');
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Map<String, XlsxStream.Cell> r, String col, Map<Integer, String> strings) {
        XlsxStream.Cell c = r.get(col);
        if (c == null) return "";
        return c.shared() ? strings.getOrDefault(c.index(), "") : c.raw();
    }

    private static Set<Integer> sharedIndexes(List<Map<String, XlsxStream.Cell>> rows, List<String> cols) {
        Set<Integer> out = new HashSet<>();
        for (Map<String, XlsxStream.Cell> r : rows) {
            for (String col : cols) {
                XlsxStream.Cell c = r.get(col);
                if (c != null && c.shared()) out.add(c.index());
            }
        }
        return out;
    }
}
