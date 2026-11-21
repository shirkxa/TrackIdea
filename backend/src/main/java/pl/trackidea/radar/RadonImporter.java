package pl.trackidea.radar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.trackidea.domain.Area;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.InstitutionType;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAD-on (radon.nauka.gov.pl, OPI PIB) – patenty i prawa ochronne polskich uczelni i instytutów
 * zgłoszone w systemie POL-on: wynalazki i wzory użytkowe z opisem, twórcami i numerem zgłoszenia.
 * Uczelnie z Podkarpacia są brane w pierwszej kolejności, potem najnowsze rekordy z całej Polski.
 */
@Component
public class RadonImporter implements SourceImporter {

    static final String PAGE = "https://radon.nauka.gov.pl/dane/patenty-i-prawa-ochronne";
    private static final String API = "https://radon.nauka.gov.pl/opendata/polon/";
    /** API zwraca najwyżej 100 rekordów na zapytanie, w kolejności niezależnej od daty – sortujemy sami. */
    private static final int PAGE_SIZE = 100;
    private static final String UPRP_CODE = "3";

    /** Filtr institutionName dopasowuje całe słowa nazwy, stąd „Krośnie”, a nie „Krosno”. */
    static final List<String> REGIONAL = List.of("Politechnika Rzeszowska", "Uniwersytet Rzeszowski",
            "Krośnie", "Wyższa Szkoła Informatyki i Zarządzania");

    /** Siedziba instytucji z rejestru RAD-on. */
    record Place(String city, String region) {
        static final Place UNKNOWN = new Place(null, "Polska");
    }

    private final HttpJson http;
    private final int perQuery;
    private final String since;
    private final Map<String, Place> places = new ConcurrentHashMap<>();

    public RadonImporter(HttpJson http,
                         @Value("${trackidea.radar.radon.per-query:8}") int perQuery,
                         @Value("${trackidea.radar.radon.since:2023-01-01}") String since) {
        this.http = http;
        this.perQuery = perQuery;
        this.since = since;
    }

    @Override
    public String name() {
        return "RAD-on";
    }

    @Override
    public String description() {
        return "Patenty i wzory użytkowe polskich uczelni i instytutów (dane POL-on); uczelnie z Podkarpacia w pierwszej kolejności";
    }

    @Override
    public String homepage() {
        return PAGE;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public List<SourceRecord> fetch() throws Exception {
        Map<String, JsonNode> chosen = new LinkedHashMap<>();
        for (String institution : REGIONAL) {
            choose(chosen, since, "&institutionName=" + URLEncoder.encode(institution, StandardCharsets.UTF_8));
        }
        // w skali kraju rekordów jest dużo – bierzemy tylko opublikowane w ostatnim roku
        String lastYear = LocalDate.now().minusYears(1).toString();
        choose(chosen, lastYear.compareTo(since) > 0 ? lastYear : since, "");
        List<SourceRecord> out = new ArrayList<>();
        for (JsonNode p : chosen.values()) {
            out.add(toRecord(p, placeOf(institutionName(p))));
        }
        return out;
    }

    /** Z jednej strony wyników zostawia {@code perQuery} najnowszych rekordów nadających się na kartę. */
    private void choose(Map<String, JsonNode> chosen, String from, String filter) throws Exception {
        String url = API + "products?resultNumbers=" + PAGE_SIZE + "&publicationDateFrom=" + from + filter;
        List<JsonNode> usable = new ArrayList<>();
        for (JsonNode p : http.get(url).path("results")) {
            if (usable(p)) {
                usable.add(p);
            }
        }
        usable.sort(Comparator.comparing((JsonNode p) -> text(p.path("publicationDate"))).reversed());
        int taken = 0;
        for (JsonNode p : usable) {
            if (taken == perQuery) {
                break;
            }
            if (chosen.putIfAbsent(ref(p), p) == null) {
                taken++;
            }
        }
    }

    /** Karta potrzebuje tytułu i opisu; odmiany roślin (same nazwy botaniczne) pomijamy. */
    static boolean usable(JsonNode p) {
        String type = text(p.path("protectionTypeCode"));
        return (type.equals("1") || type.equals("2")) && !title(p).isBlank()
                && text(p.path("productDescription")).length() >= 80 && !institutionName(p).isBlank();
    }

    static SourceRecord toRecord(JsonNode p, Place place) {
        String institution = institutionName(p);
        String applicationNumber = text(p.path("applicationNumber"));
        String applicationDate = text(p.path("applicationDate"));
        String publicationNumber = text(p.path("publicationNumber"));
        String publicationDate = text(p.path("publicationDate"));
        List<String> facts = new ArrayList<>();
        facts.add("Rodzaj ochrony: " + text(p.path("protectionType")));
        String office = text(p.path("grantingInstitutionName"));
        if (!office.isBlank()) facts.add("Organ udzielający ochrony: " + office);
        if (!applicationNumber.isBlank()) {
            facts.add("Numer zgłoszenia: " + applicationNumber + (applicationDate.isBlank() ? "" : " (z " + applicationDate + ")"));
        }
        if (!publicationNumber.isBlank() && !publicationNumber.equals(applicationNumber)) {
            facts.add("Numer publikacji: " + publicationNumber);
        }
        if (!publicationDate.isBlank()) facts.add("Data publikacji: " + publicationDate);
        String protectionRegion = text(p.path("protectionRegion"));
        if (!protectionRegion.isBlank()) facts.add("Zasięg ochrony: " + protectionRegion);
        List<String> holders = new ArrayList<>();
        for (JsonNode i : p.path("relatedInstitutions")) {
            String name = text(i.path("institutionName"));
            if (!name.isBlank() && !holders.contains(name)) holders.add(name);
        }
        if (holders.size() > 1) facts.add("Instytucje powiązane: " + String.join(", ", holders));
        Integer year = publicationDate.length() >= 4 ? Integer.valueOf(publicationDate.substring(0, 4)) : null;
        return new SourceRecord("RAD-on", ref(p), Names.shorten(title(p), 240),
                Names.shorten(text(p.path("productDescription")), 2500), institution,
                Names.institutionType(institution, InstitutionType.UCZELNIA), place.city(), null, place.region(),
                inventors(p.path("inventors")), year, List.of(), url(p), InnovationType.TECHNICZNA, null, null, facts,
                Area.TECHNOLOGICZNY);
    }

    /** Identyfikator w źródle: dla zgłoszeń w UPRP numer z prefiksem rejestru (P – wynalazek, W – wzór użytkowy). */
    static String ref(JsonNode p) {
        String number = text(p.path("applicationNumber"));
        if (UPRP_CODE.equals(text(p.path("grantingInstitutionCode"))) && number.matches("\\d{6}")) {
            return (text(p.path("protectionTypeCode")).equals("2") ? "W." : "P.") + number;
        }
        // zgłoszenie w urzędzie zagranicznym: numer bez prefiksu rejestru UPRP
        return number.isBlank() ? text(p.path("protectionUuid")) : number;
    }

    /** Zgłoszenia w Urzędzie Patentowym RP mają publiczną kartę w e-Wyszukiwarce UPRP. */
    static String url(JsonNode p) {
        String ref = ref(p);
        return ref.matches("[PW]\\.\\d{6}") ? "https://ewyszukiwarka.pue.uprp.gov.pl/search/pwp-details/" + ref : PAGE;
    }

    static String title(JsonNode p) {
        for (JsonNode t : p.path("productTitles")) {
            if ("polski".equals(text(t.path("code"))) && !text(t.path("value")).isBlank()) {
                return text(t.path("value")).trim();
            }
        }
        return text(p.path("protectionTitle")).trim();
    }

    static String institutionName(JsonNode p) {
        for (JsonNode i : p.path("relatedInstitutions")) {
            String name = text(i.path("institutionName"));
            if (!name.isBlank()) return name.trim();
        }
        return "";
    }

    static String inventors(JsonNode inventors) {
        List<String> names = new ArrayList<>();
        for (JsonNode group : inventors) {
            for (JsonNode person : group.path("persons")) {
                String name = (text(person.path("firstName")) + " " + text(person.path("lastName"))).trim();
                if (!name.isBlank() && !names.contains(name)) names.add(name);
            }
        }
        return names.size() > 3 ? String.join(", ", names.subList(0, 3)) + " i in." : String.join(", ", names);
    }

    /** Miasto i województwo instytucji z rejestru RAD-on; zapamiętywane, bo instytucje się powtarzają. */
    private Place placeOf(String institution) {
        return places.computeIfAbsent(institution, name -> {
            try {
                String url = API + "institutions?resultNumbers=10&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
                return place(http.get(url).path("results"), name);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return Place.UNKNOWN;
            }
        });
    }

    static Place place(JsonNode institutions, String name) {
        for (JsonNode i : institutions) {
            if (name.equalsIgnoreCase(text(i.path("name")).trim())) {
                for (JsonNode a : i.path("addresses")) {
                    String region = text(a.path("voivodeship"));
                    if (!region.isBlank()) {
                        String city = text(a.path("city"));
                        return new Place(city.isBlank() ? null : city, region.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return Place.UNKNOWN;
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? "" : n.asString("");
    }
}
