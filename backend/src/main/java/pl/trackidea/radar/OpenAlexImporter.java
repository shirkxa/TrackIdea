package pl.trackidea.radar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Sector;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OpenAlex (openalex.org) – otwarta baza publikacji naukowych, odpowiednik Google Scholar z darmowym API.
 * Pobiera prace polskich uczelni, instytutów i urzędów w trzech obszarach z wyzwania:
 * technologicznym, edukacyjnym i administracyjnym.
 */
@Component
public class OpenAlexImporter implements SourceImporter {

    private record Query(String search, InnovationType type, Sector sector) {
    }

    private static final List<Query> QUERIES = List.of(
            new Query("prototype implementation deployed system", InnovationType.TECHNICZNA, null),
            new Query("innovative teaching method school students", InnovationType.EDUKACYJNA, Sector.EDUKACJA),
            new Query("public administration digital services municipality", InnovationType.PROCESOWA, Sector.ADMINISTRACJA));

    private static final String SELECT =
            "id,doi,title,publication_year,abstract_inverted_index,authorships,primary_topic,keywords,primary_location,type,cited_by_count";

    private final HttpJson http;
    private final int perQuery;
    private final String since;

    public OpenAlexImporter(HttpJson http,
                            @Value("${trackidea.radar.openalex.per-query:8}") int perQuery,
                            @Value("${trackidea.radar.openalex.since:2023-01-01}") String since) {
        this.http = http;
        this.perQuery = perQuery;
        this.since = since;
    }

    @Override
    public String name() {
        return "OpenAlex";
    }

    @Override
    public String description() {
        return "Publikacje i wyniki prac polskich uczelni, instytutów i urzędów (nauka i technologie)";
    }

    @Override
    public String homepage() {
        return "https://openalex.org";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public List<SourceRecord> fetch() throws Exception {
        List<SourceRecord> out = new ArrayList<>();
        for (Query q : QUERIES) {
            String url = "https://api.openalex.org/works?filter=institutions.country_code:pl,from_publication_date:" + since
                    + ",has_abstract:true,type:article&search=" + URLEncoder.encode(q.search(), StandardCharsets.UTF_8)
                    + "&per-page=" + perQuery + "&select=" + SELECT;
            for (JsonNode w : http.get(url).path("results")) {
                SourceRecord r = toRecord(w, q);
                if (r != null) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    private SourceRecord toRecord(JsonNode w, Query q) {
        String title = w.path("title").asString("");
        String abstractText = abstractFrom(w.path("abstract_inverted_index"));
        JsonNode inst = polishInstitution(w.path("authorships"));
        if (title.isBlank() || abstractText.isBlank() || inst == null) {
            return null;
        }
        List<String> authors = new ArrayList<>();
        for (JsonNode a : w.path("authorships")) {
            if (authors.size() == 3) {
                authors.add("i in.");
                break;
            }
            authors.add(a.path("author").path("display_name").asString(""));
        }
        List<String> keywords = new ArrayList<>();
        for (JsonNode k : w.path("keywords")) {
            if (keywords.size() < 5) keywords.add(k.path("display_name").asString(""));
        }
        String doi = w.path("doi").asString(null);
        String landing = w.path("primary_location").path("landing_page_url").asString(null);
        Sector sector = q.sector() != null ? q.sector() : sectorFrom(w.path("primary_topic"));
        List<String> facts = new ArrayList<>();
        String kind = w.path("type").asString("");
        String venue = w.path("primary_location").path("source").path("display_name").asString("");
        facts.add("Typ publikacji: " + kindLabel(kind) + (venue.isBlank() ? "" : " – " + venue));
        if (w.path("publication_year").isNumber()) facts.add("Rok publikacji: " + w.path("publication_year").asInt());
        facts.add("Liczba cytowań (OpenAlex): " + w.path("cited_by_count").asInt(0));
        facts.add("Polska instytucja autorów: " + inst.path("display_name").asString(""));
        if (doi != null) facts.add("DOI: " + doi.replace("https://doi.org/", ""));
        return new SourceRecord(name(), shortId(w.path("id").asString("")), Names.shorten(title, 240),
                Names.shorten(abstractText, 2500), inst.path("display_name").asString(""),
                institutionType(inst.path("type").asString("")), null, null, "Polska",
                String.join(", ", authors), w.path("publication_year").isNumber() ? w.path("publication_year").asInt() : null,
                keywords, doi != null ? doi : landing, q.type(), sector, null, facts);
    }

    static String kindLabel(String type) {
        return switch (type) {
            case "article" -> "artykuł";
            case "book-chapter" -> "rozdział w książce";
            case "book" -> "książka";
            case "dissertation" -> "rozprawa";
            case "preprint" -> "preprint";
            case "report" -> "raport";
            default -> type.isBlank() ? "publikacja" : type;
        };
    }

    /** OpenAlex przechowuje abstrakt jako indeks odwrotny: słowo → pozycje. */
    static String abstractFrom(JsonNode index) {
        if (index == null || !index.isObject()) {
            return "";
        }
        TreeMap<Integer, String> words = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : index.properties()) {
            for (JsonNode pos : e.getValue()) {
                words.put(pos.asInt(), e.getKey());
            }
        }
        return String.join(" ", words.values());
    }

    private static JsonNode polishInstitution(JsonNode authorships) {
        for (JsonNode a : authorships) {
            for (JsonNode i : a.path("institutions")) {
                if ("PL".equals(i.path("country_code").asString(""))) {
                    return i;
                }
            }
        }
        return null;
    }

    private static InstitutionType institutionType(String type) {
        return switch (type) {
            case "education" -> InstitutionType.UCZELNIA;
            case "company" -> InstitutionType.FIRMA;
            case "nonprofit" -> InstitutionType.NGO;
            default -> InstitutionType.INSTYTUCJA_PUBLICZNA;
        };
    }

    /** Sektor z klasyfikacji tematycznej OpenAlex (dziedzina / poddziedzina). */
    static Sector sectorFrom(JsonNode topic) {
        String field = topic.path("field").path("display_name").asString("");
        String subfield = topic.path("subfield").path("display_name").asString("");
        if (subfield.contains("Aerospace")) return Sector.LOTNICTWO;
        if (subfield.contains("Automotive")) return Sector.MOTORYZACJA;
        if (subfield.contains("Astronomy")) return Sector.KOSMOS;
        if (subfield.contains("Education")) return Sector.EDUKACJA;
        if (subfield.contains("Public Administration")) return Sector.ADMINISTRACJA;
        return switch (field) {
            case "Computer Science", "Engineering", "Mathematics" -> Sector.ICT;
            case "Energy" -> Sector.ENERGETYKA;
            case "Agricultural and Biological Sciences" -> Sector.ROLNICTWO;
            case "Environmental Science", "Earth and Planetary Sciences" -> Sector.SRODOWISKO;
            case "Medicine", "Health Professions", "Nursing", "Pharmacology, Toxicology and Pharmaceutics" -> Sector.ZDROWIE;
            case "Social Sciences", "Psychology" -> Sector.JAKOSC_ZYCIA;
            default -> null;
        };
    }

    private static String shortId(String id) {
        return id.substring(id.lastIndexOf('/') + 1);
    }
}
