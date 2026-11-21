package pl.trackidea.radar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CORDIS (cordis.europa.eu) – projekty badawcze i innowacyjne finansowane przez UE.
 * Bierze projekty z udziałem polskich gmin, szkół, uczelni i firm.
 */
@Component
public class CordisImporter implements SourceImporter {

    /** Zapytanie + słowa, po których rozpoznajemy właściwą polską organizację (np. gminę albo szkołę). */
    private record Query(String q, InnovationType type, Sector sector, List<String> orgWords) {
    }

    private static final List<Query> QUERIES = List.of(
            new Query("contenttype='project' AND ('GMINA' OR 'URZAD MIASTA' OR 'POWIAT' OR 'WOJEWODZTWO')",
                    InnovationType.PROCESOWA, Sector.ADMINISTRACJA,
                    List.of("GMINA", "URZAD", "MIASTO", "POWIAT", "WOJEWODZTW")),
            new Query("contenttype='project' AND ('SZKOLA PODSTAWOWA' OR 'LICEUM' OR 'ZESPOL SZKOL' OR 'TECHNIKUM')",
                    InnovationType.EDUKACYJNA, Sector.EDUKACJA,
                    List.of("SZKOLA PODSTAWOWA", "LICEUM", "ZESPOL SZKOL", "TECHNIKUM", "PRZEDSZKOLE")),
            new Query("contenttype='project' AND ('POLITECHNIKA' OR 'SPOLKA Z OGRANICZONA')",
                    InnovationType.TECHNICZNA, null, List.of()));

    private final HttpJson http;
    private final int perQuery;

    public CordisImporter(HttpJson http, @Value("${trackidea.radar.cordis.per-query:8}") int perQuery) {
        this.http = http;
        this.perQuery = perQuery;
    }

    @Override
    public String name() {
        return "CORDIS";
    }

    @Override
    public String description() {
        return "Projekty badawcze i innowacyjne UE z udziałem polskich gmin, szkół, uczelni i firm";
    }

    @Override
    public String homepage() {
        return "https://cordis.europa.eu";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public List<SourceRecord> fetch() throws Exception {
        List<SourceRecord> out = new ArrayList<>();
        for (Query q : QUERIES) {
            // z zapasem: część wyników nie ma polskiego uczestnika i zostanie pominięta
            String url = "https://cordis.europa.eu/search/en?q=" + URLEncoder.encode(q.q(), StandardCharsets.UTF_8)
                    + "&format=json&p=1&num=" + (perQuery * 2) + "&srt=/project/contentUpdateDate:decreasing";
            int taken = 0;
            for (JsonNode hit : asList(http.get(url).path("hits").path("hit"))) {
                SourceRecord r = toRecord(hit.path("project"), q);
                if (r != null && taken < perQuery) {
                    out.add(r);
                    taken++;
                }
            }
        }
        return out;
    }

    private SourceRecord toRecord(JsonNode p, Query q) {
        String title = p.path("title").asString("");
        String objective = p.path("objective").asString("");
        JsonNode orgs = p.path("relations").path("associations").path("organization");
        JsonNode org = organizationWith(orgs, q.orgWords());
        boolean matched = org != null;
        if (org == null) {
            org = polishOrganization(orgs);
        }
        if (title.isBlank() || objective.isBlank() || org == null) {
            return null;
        }
        // obszar z zapytania przypisujemy tylko, gdy w projekcie faktycznie jest gmina / szkoła
        InnovationType type = q.orgWords().isEmpty() || matched ? q.type() : null;
        Sector sector = q.orgWords().isEmpty() || matched ? q.sector() : null;
        String name = Names.titleCase(org.path("legalName").asString(org.path("shortName").asString("")));
        List<String> keywords = new ArrayList<>();
        for (JsonNode c : asList(p.path("relations").path("categories").path("category"))) {
            if ("euroSciVoc".equals(c.path("@attributes").path("classification").asString("")) && keywords.size() < 5) {
                keywords.add(c.path("title").asString(""));
            }
        }
        String start = p.path("startDate").asString("");
        Integer year = start.length() >= 4 ? Integer.valueOf(start.substring(0, 4)) : null;
        String status = p.path("status").asString("");
        // szacunek: zakończony projekt = rozwiązanie po wdrożeniu pilotażowym, trwający = etap testów
        Maturity maturity = "CLOSED".equals(status) ? Maturity.PILOTAZ : Maturity.TESTY;
        String acronym = p.path("acronym").asString("");
        List<String> facts = new ArrayList<>();
        facts.add("Status projektu: " + statusLabel(status));
        String end = p.path("endDate").asString("");
        if (!start.isBlank()) facts.add("Okres realizacji: " + start + (end.isBlank() ? "" : " – " + end));
        String programme = programme(p);
        if (programme != null) facts.add("Program: " + programme);
        String eu = p.path("ecMaxContribution").asString("");
        if (!eu.isBlank()) facts.add("Dofinansowanie UE: " + euro(eu));
        facts.add("Polska organizacja w konsorcjum: " + name);
        int results = asList(p.path("relations").path("associations").path("result")).size();
        if (results > 0) facts.add("Opublikowane wyniki projektu w CORDIS: " + results);
        return new SourceRecord(name(), p.path("id").asString(""),
                Names.shorten((acronym.isBlank() ? "" : acronym + ": ") + title, 240), Names.shorten(objective, 2500),
                name, institutionType(org, name), Names.titleCase(org.path("address").path("city").asString(null)),
                null, "Polska", "konsorcjum projektu " + (acronym.isBlank() ? "" : acronym), year, keywords,
                "https://cordis.europa.eu/project/id/" + p.path("id").asString(""), type, sector, maturity, facts);
    }

    static String statusLabel(String status) {
        return switch (status) {
            case "CLOSED" -> "zakończony";
            case "SIGNED" -> "w realizacji";
            case "TERMINATED" -> "przerwany";
            default -> status.isBlank() ? "brak informacji" : status.toLowerCase(Locale.ROOT);
        };
    }

    /** Kwota w euro z separatorem tysięcy, np. „4 998 765 €”. */
    static String euro(String amount) {
        try {
            long v = Math.round(Double.parseDouble(amount));
            return String.format(Locale.ROOT, "%,d €", v).replace(',', ' ');
        } catch (NumberFormatException e) {
            return amount + " €";
        }
    }

    private static String programme(JsonNode p) {
        for (JsonNode prog : asList(p.path("relations").path("associations").path("programme"))) {
            String fp = prog.path("frameworkProgramme").asString("");
            String title = prog.path("title").asString("");
            if (!fp.isBlank()) {
                return fp.replace("HORIZON", "Horyzont Europa") + (title.isBlank() ? "" : " – " + title);
            }
        }
        return null;
    }

    /** Polska organizacja, której nazwa zawiera jedno ze słów (np. gmina w projekcie z kilkoma uczelniami). */
    private static JsonNode organizationWith(JsonNode orgs, List<String> words) {
        for (JsonNode o : asList(orgs)) {
            String n = o.path("legalName").asString("").toUpperCase(Locale.ROOT);
            if ("PL".equals(o.path("address").path("country").asString("")) && words.stream().anyMatch(n::contains)) {
                return o;
            }
        }
        return null;
    }

    /** Pierwsza polska organizacja w projekcie (koordynator, jeśli jest polski). */
    private static JsonNode polishOrganization(JsonNode orgs) {
        JsonNode first = null;
        for (JsonNode o : asList(orgs)) {
            if ("PL".equals(o.path("address").path("country").asString(""))) {
                if ("coordinator".equals(o.path("@attributes").path("type").asString(""))) {
                    return o;
                }
                if (first == null) {
                    first = o;
                }
            }
        }
        return first;
    }

    private static InstitutionType institutionType(JsonNode org, String name) {
        String code = "";
        for (JsonNode c : asList(org.path("relations").path("categories").path("category"))) {
            code = c.path("code").asString(code);
        }
        String n = name.toUpperCase(Locale.ROOT);
        if (n.contains("SP. Z O.O.") || n.contains("SPOLKA") || n.contains("S.A.")) {
            return InstitutionType.FIRMA;
        }
        if (n.contains("SZKOLA PODSTAWOWA") || n.contains("LICEUM") || n.contains("ZESPOL SZKOL") || n.contains("TECHNIKUM")) {
            return InstitutionType.SZKOLA;
        }
        return switch (code) {
            case "HES" -> InstitutionType.UCZELNIA;
            case "PRC" -> InstitutionType.FIRMA;
            case "PUB" -> n.contains("GMINA") || n.contains("URZAD") || n.contains("POWIAT") || n.contains("WOJEW")
                    || n.contains("MIAST") ? InstitutionType.JST : InstitutionType.INSTYTUCJA_PUBLICZNA;
            case "OTH" -> n.contains("SZKOL") ? InstitutionType.SZKOLA : InstitutionType.NGO;
            default -> n.contains("SZKOL") ? InstitutionType.SZKOLA : InstitutionType.INSTYTUCJA_PUBLICZNA;
        };
    }

    /** CORDIS zwraca pojedynczy obiekt zamiast listy, gdy element jest jeden. */
    private static List<JsonNode> asList(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return out;
        }
        if (node.isArray()) {
            node.forEach(out::add);
        } else {
            out.add(node);
        }
        return out;
    }
}
