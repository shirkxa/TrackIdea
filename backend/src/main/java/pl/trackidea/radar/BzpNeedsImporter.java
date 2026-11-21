package pl.trackidea.radar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Sector;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Biuletyn Zamówień Publicznych (ezamowienia.gov.pl, Urząd Zamówień Publicznych) – ogłoszenia o zamówieniach
 * jako potrzeby: gmina, szkoła, szpital lub uczelnia szuka wykonawcy konkretnego rozwiązania.
 * Z tysięcy ogłoszeń wybieramy te, w których instytucja chce coś wdrożyć, zbudować lub zbadać
 * (słowo „wdrożenie” w nazwie, usługi informatyczne, oprogramowanie, usługi badawcze), a nie utrzymać to, co ma.
 * Zamawiający z Podkarpacia są brani w pierwszej kolejności; ogłoszenie znika z tablicy po terminie składania ofert.
 */
@Component
public class BzpNeedsImporter implements NeedSource {

    static final String NAME = "BZP";
    private static final String API = "https://ezamowienia.gov.pl/mo-board/api/v1/notice";
    private static final String NOTICE_PAGE = "https://ezamowienia.gov.pl/mo-client-board/bzp/notice-details/id/";
    /** API zwraca pełną treść każdego ogłoszenia i nie stronicuje wyników – strona musi być mała. */
    private static final int PAGE_SIZE = 40;
    private static final String PODKARPACKIE = "PL18";

    /** Jedno zapytanie do BZP: filtr API, czy sprawdzać słowa w nazwie i czy zamawiający chce gotowego rozwiązania. */
    record Query(String filter, boolean checkTitle, boolean wantsReady) {
    }

    static final List<Query> QUERIES = List.of(
            new Query("OrderObject=" + URLEncoder.encode("wdroż", StandardCharsets.UTF_8), true, true),
            new Query("CpvCode=72000000-5", true, true),   // usługi informatyczne
            new Query("CpvCode=48000000-8", true, true),   // pakiety oprogramowania i systemy informatyczne
            new Query("CpvCode=73000000-2", false, false)); // usługi badawcze i eksperymentalno-rozwojowe

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    /** Zamawiający szuka nowego rozwiązania… */
    private static final Pattern WANTS_SOLUTION = Pattern.compile(
            "wdroż|zaprojektowan|opracowan\\w+ (systemu|aplikacji|platformy|portalu|oprogramowania|narzędzia)|budow\\w+ (systemu|aplikacji|platformy|portalu)"
                    + "|e-?usług|platform|aplikacj|portal|innowac|prototyp|badawcz|cyfryzac|automatyz|sztuczn\\w+ inteligencj|rozwiązan",
            FLAGS);
    /** …a nie utrzymania, serwisu lub licencji do tego, co już ma… */
    private static final Pattern MAINTENANCE = Pattern.compile(
            "nadz[oó]r\\w* autorski|serwis(?!\\w*\\s+internetow)|opiek\\w+ (gwarancyjn|powdrożeniow|techniczn)|asyst\\w+ techniczn|wsparci\\w+ (merytoryczno-)?techniczn"
                    + "|subskrypcj|licencj|przedłużeni|odnowieni|dostęp\\w* do (systemu|platformy|bazy)|usług\\w* (wsparcia|utrzymania|i rozwoju)",
            FLAGS);
    /** …ani samego sprzętu. */
    private static final Pattern HARDWARE = Pattern.compile(
            "serwer|macierz|dysk\\w* tward|komputer|laptop|monitor\\w* ekran|kolokacj|zasilacz|drukar|tablet", FLAGS);
    /** Opis zaczynający się od licencji lub subskrypcji to zakup gotowego produktu, nie zapotrzebowanie na rozwiązanie. */
    private static final Pattern LICENCE = Pattern.compile("^\\W*(dostawa|zakup|sprzedaż)[^.]{0,40}(licencj|subskrypcj)", FLAGS);

    private static final Pattern CPV = Pattern.compile("(\\d{8})-\\d \\((.*?)\\)(?=,\\d{8}-\\d \\(|$)");
    private static final Pattern DESCRIPTION = Pattern.compile(
            "Krótki opis przedmiotu zamówienia\\s*(.*?)(?=\\n\\s*\\d+\\.\\d+(?:\\.\\d+)*\\.\\)|\\n\\s*SEKCJA |$)", Pattern.DOTALL);

    private static final Map<String, String> PROVINCES = Map.ofEntries(
            Map.entry("PL02", "dolnośląskie"), Map.entry("PL04", "kujawsko-pomorskie"), Map.entry("PL06", "lubelskie"),
            Map.entry("PL08", "lubuskie"), Map.entry("PL10", "łódzkie"), Map.entry("PL12", "małopolskie"),
            Map.entry("PL14", "mazowieckie"), Map.entry("PL16", "opolskie"), Map.entry("PL18", "podkarpackie"),
            Map.entry("PL20", "podlaskie"), Map.entry("PL22", "pomorskie"), Map.entry("PL24", "śląskie"),
            Map.entry("PL26", "świętokrzyskie"), Map.entry("PL28", "warmińsko-mazurskie"),
            Map.entry("PL30", "wielkopolskie"), Map.entry("PL32", "zachodniopomorskie"));

    private final HttpJson http;
    private final int perQuery;
    private final int days;
    private final int daysNational;

    public BzpNeedsImporter(HttpJson http,
                            @Value("${trackidea.radar.bzp.per-query:6}") int perQuery,
                            @Value("${trackidea.radar.bzp.days:30}") int days,
                            @Value("${trackidea.radar.bzp.days-national:7}") int daysNational) {
        this.http = http;
        this.perQuery = perQuery;
        this.days = days;
        this.daysNational = daysNational;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Ogłoszenia o zamówieniach publicznych jako potrzeby: wdrożenia, systemy informatyczne, usługi badawcze; Podkarpacie w pierwszej kolejności";
    }

    @Override
    public String homepage() {
        return "https://ezamowienia.gov.pl/mo-client-board/bzp/list";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public List<NeedRecord> fetch() throws Exception {
        Instant now = Instant.now();
        Map<String, NeedRecord> out = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Query q : QUERIES) {
            choose(out, seen, q, "&OrganizationProvince=" + PODKARPACKIE, days, now);
        }
        // w skali kraju ogłoszeń jest dużo, a API oddaje najstarsze z okna – stąd krótsze okno
        for (Query q : QUERIES) {
            choose(out, seen, q, "", daysNational, now);
        }
        return new ArrayList<>(out.values());
    }

    /** Z jednego zapytania zostawia {@code perQuery} najnowszych, jeszcze otwartych ogłoszeń. */
    private void choose(Map<String, NeedRecord> out, Set<String> seen, Query q, String scope, int window, Instant now)
            throws Exception {
        Instant to = now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS);
        Instant from = to.minus(Duration.ofDays(window + 1L));
        String url = API + "?NoticeType=ContractNotice&PageSize=" + PAGE_SIZE + "&PublicationDateFrom=" + from
                + "&PublicationDateTo=" + to + scope + "&" + q.filter();
        List<NeedRecord> found = new ArrayList<>();
        for (JsonNode notice : http.get(url)) {
            NeedRecord r = toRecord(notice, q, now);
            if (r != null) {
                found.add(r);
            }
        }
        found.sort(Comparator.comparing(NeedRecord::publishedAt).reversed());
        int taken = 0;
        for (NeedRecord r : found) {
            if (taken == perQuery) {
                break;
            }
            // to samo zamówienie bywa ogłaszane ponownie – zostaje najnowsze ogłoszenie
            if (seen.add(sameOrderKey(r)) && out.putIfAbsent(r.ref(), r) == null) {
                taken++;
            }
        }
    }

    /** Ogłoszenie → potrzeba; {@code null}, gdy termin minął albo chodzi o utrzymanie istniejącego rozwiązania. */
    static NeedRecord toRecord(JsonNode n, Query q, Instant now) {
        String ref = text(n.path("bzpNumber"));
        String object = text(n.path("orderObject")).replaceAll("\\s+", " ").trim();
        String buyer = text(n.path("organizationName")).replaceAll("\\s+", " ").trim();
        Instant deadline = instant(text(n.path("submittingOffersDate")));
        Instant published = instant(text(n.path("publicationDate")));
        if (ref.isBlank() || object.isBlank() || buyer.isBlank() || (deadline != null && !deadline.isAfter(now))) {
            return null;
        }
        if (MAINTENANCE.matcher(object).find() || HARDWARE.matcher(object).find()
                || (q.checkTitle() && !WANTS_SOLUTION.matcher(object).find())) {
            return null;
        }
        String institution = Names.titleCase(buyer.replaceAll("^\"|\"$", ""));
        InstitutionType type = Names.institutionType(institution, InstitutionType.INSTYTUCJA_PUBLICZNA);
        Map<String, String> cpv = cpv(text(n.path("cpvCode")));
        String description = description(text(n.path("htmlBody")));
        if (LICENCE.matcher(description).find()) {
            return null;
        }
        String objectId = text(n.path("objectId"));
        String city = text(n.path("organizationCity")).trim();
        return new NeedRecord(NAME, ref, Names.shorten(object.replaceAll("^[„\"]|[”\"]$", ""), 240),
                // opis powtarzający nazwę zamówienia nic nie wnosi – karta pokaże samą nazwę
                description.isBlank() || description.equalsIgnoreCase(object) ? null : Names.shorten(description, 2500),
                institution, type,
                city.isBlank() ? null : city, PROVINCES.getOrDefault(text(n.path("organizationProvince")), "Polska"),
                sector(cpv.keySet(), institution, type), tags(cpv.values()),
                objectId.isBlank() ? null : NOTICE_PAGE + objectId, published == null ? now : published, deadline,
                q.wantsReady());
    }

    /** „Krótki opis przedmiotu zamówienia” z treści ogłoszenia (punkt 4.2.2). */
    static String description(String html) {
        if (html.isBlank()) {
            return "";
        }
        String plain = HtmlUtils.htmlUnescape(html.replaceAll("(?is)<style[^>]*>.*?</style>", " ").replaceAll("<[^>]+>", "\n"))
                .replace(' ', ' ');
        Matcher m = DESCRIPTION.matcher(plain);
        return m.find() ? m.group(1).replaceAll("\\s+", " ").trim() : "";
    }

    /** Kody CPV z pola „72000000-5 (Usługi informatyczne…),48000000-8 (…)”: kod → nazwa, w kolejności ogłoszenia. */
    static Map<String, String> cpv(String field) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = CPV.matcher(field);
        while (m.find()) {
            out.putIfAbsent(m.group(1), m.group(2).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim());
        }
        return out;
    }

    /** Sektor potrzeby: najpierw dziedzina zamawiającego (szpital, szkoła), potem przedmiot zamówienia z kodu CPV. */
    static Sector sector(Collection<String> cpvCodes, String institution, InstitutionType type) {
        String name = institution.toUpperCase(Locale.ROOT);
        if (name.matches(".*(SZPITAL|ZDROWI|MEDYCZN|ZOZ|POGOTOWI).*")) return Sector.ZDROWIE;
        if (type == InstitutionType.SZKOLA) return Sector.EDUKACJA;
        boolean ict = false;
        boolean training = false;
        for (String code : cpvCodes) {
            switch (code.substring(0, 2)) {
                case "80" -> training = true;
                case "33", "85" -> { return Sector.ZDROWIE; }
                case "90" -> { return Sector.SRODOWISKO; }
                case "09", "65" -> { return Sector.ENERGETYKA; }
                case "03", "16", "77" -> { return Sector.ROLNICTWO; }
                case "34" -> { return Sector.MOTORYZACJA; }
                case "75" -> { return Sector.ADMINISTRACJA; }
                case "30", "32", "48", "72" -> ict = true;
                default -> { }
            }
        }
        if (type == InstitutionType.JST) return Sector.ADMINISTRACJA;
        // szkolenie dołączone do dostawy systemu nie robi z zamówienia potrzeby edukacyjnej
        return ict ? Sector.ICT : training ? Sector.EDUKACJA : null;
    }

    private static List<String> tags(Collection<String> cpvNames) {
        List<String> out = new ArrayList<>();
        for (String name : cpvNames) {
            if (out.size() < 4 && name.length() <= 60) {
                out.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String sameOrderKey(NeedRecord r) {
        return (r.institution() + "|" + r.title().replaceAll("\\s*\\(\\d+\\)\\s*$", "")).toLowerCase(Locale.ROOT);
    }

    private static Instant instant(String iso) {
        try {
            return iso.isBlank() ? null : Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? "" : n.asString("");
    }
}
