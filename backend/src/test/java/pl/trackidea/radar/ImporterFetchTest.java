package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pełna ścieżka importerów (zapytania → wybór → rekord) na przygotowanych odpowiedziach API, bez internetu. */
class ImporterFetchTest {

    // ---------- OpenAlex ----------

    private static final String OPENALEX = """
            {"results":[
              {"id":"https://openalex.org/W42","doi":"https://doi.org/10.1/abc","title":"Prototype of a road defect map",
               "publication_year":2024,"type":"article","cited_by_count":7,
               "abstract_inverted_index":{"Residents":[0],"report":[1],"defects":[2]},
               "authorships":[
                 {"author":{"display_name":"A One"},"institutions":[{"country_code":"DE","display_name":"TU Berlin","type":"education"}]},
                 {"author":{"display_name":"B Two"},"institutions":[{"country_code":"PL","display_name":"Politechnika Rzeszowska","type":"education"}]},
                 {"author":{"display_name":"C Three"},"institutions":[]},
                 {"author":{"display_name":"D Four"},"institutions":[]}],
               "primary_topic":{"field":{"display_name":"Energy"},"subfield":{"display_name":"Renewable Energy"}},
               "keywords":[{"display_name":"roads"},{"display_name":"maps"}],
               "primary_location":{"landing_page_url":"https://example.org/paper","source":{"display_name":"Journal X"}}},
              {"id":"https://openalex.org/W43","title":"Work without a Polish institution","publication_year":2024,
               "abstract_inverted_index":{"Text":[0]},
               "authorships":[{"author":{"display_name":"E Five"},"institutions":[{"country_code":"FR","display_name":"Sorbonne","type":"education"}]}]},
              {"id":"https://openalex.org/W44","doi":null,"title":"Company report","type":"report",
               "abstract_inverted_index":{"Deployed":[0],"system":[1]},
               "authorships":[{"author":{"display_name":"F Six"},"institutions":[{"country_code":"PL","display_name":"Firma X","type":"company"}]}],
               "primary_location":{"landing_page_url":"https://example.org/report"}}
            ]}""";

    @Test
    void openAlexTurnsWorksOfPolishInstitutionsIntoRecords() throws Exception {
        FakeHttp http = new FakeHttp().on("api.openalex.org", OPENALEX);

        List<SourceRecord> records = new OpenAlexImporter(http, 4, "2023-01-01", "").fetch();

        // trzy zapytania (technologie, edukacja, administracja) × dwie prace z polską instytucją
        assertThat(records).hasSize(6);
        assertThat(http.requested).hasSize(3).allSatisfy(url ->
                assertThat(url).contains("institutions.country_code:pl", "from_publication_date:2023-01-01", "per-page=4"));
        SourceRecord r = records.getFirst();
        assertThat(r.source()).isEqualTo("OpenAlex");
        assertThat(r.ref()).isEqualTo("W42");
        assertThat(r.title()).isEqualTo("Prototype of a road defect map");
        assertThat(r.abstractText()).isEqualTo("Residents report defects");
        assertThat(r.institution()).isEqualTo("Politechnika Rzeszowska");
        assertThat(r.institutionType()).isEqualTo(InstitutionType.UCZELNIA);
        assertThat(r.authors()).isEqualTo("A One, B Two, C Three, i in.");
        assertThat(r.year()).isEqualTo(2024);
        assertThat(r.keywords()).containsExactly("roads", "maps");
        assertThat(r.url()).isEqualTo("https://doi.org/10.1/abc");
        assertThat(r.typeHint()).isEqualTo(InnovationType.TECHNICZNA);
        // zapytanie technologiczne nie narzuca sektora – bierzemy go z klasyfikacji tematycznej pracy
        assertThat(r.sectorHint()).isEqualTo(Sector.ENERGETYKA);
        assertThat(r.facts()).contains("Typ publikacji: artykuł – Journal X", "Rok publikacji: 2024",
                "Liczba cytowań (OpenAlex): 7", "Polska instytucja autorów: Politechnika Rzeszowska", "DOI: 10.1/abc");

        SourceRecord report = records.get(1);
        assertThat(report.institutionType()).isEqualTo(InstitutionType.FIRMA);
        assertThat(report.url()).isEqualTo("https://example.org/report");
        assertThat(report.year()).isNull();
        assertThat(report.facts()).contains("Typ publikacji: raport");
        // zapytanie edukacyjne i administracyjne narzucają sektor
        assertThat(records.get(2).sectorHint()).isEqualTo(Sector.EDUKACJA);
        assertThat(records.get(4).sectorHint()).isEqualTo(Sector.ADMINISTRACJA);
    }

    @Test
    void openAlexDescribesItselfAndLabelsPublicationKinds() {
        OpenAlexImporter importer = new OpenAlexImporter(new FakeHttp(), 8, "2023-01-01", "");
        assertThat(importer.name()).isEqualTo("OpenAlex");
        assertThat(importer.live()).isTrue();
        assertThat(importer.homepage()).startsWith("https://");
        assertThat(importer.description()).isNotBlank();
        assertThat(OpenAlexImporter.kindLabel("book-chapter")).isEqualTo("rozdział w książce");
        assertThat(OpenAlexImporter.kindLabel("book")).isEqualTo("książka");
        assertThat(OpenAlexImporter.kindLabel("dissertation")).isEqualTo("rozprawa");
        assertThat(OpenAlexImporter.kindLabel("preprint")).isEqualTo("preprint");
        assertThat(OpenAlexImporter.kindLabel("")).isEqualTo("publikacja");
        assertThat(OpenAlexImporter.kindLabel("dataset")).isEqualTo("dataset");
    }

    @Test
    void sourceErrorIsPassedToRadar() {
        FakeHttp http = new FakeHttp();
        http.failure = new IOException("HTTP 429 z api.openalex.org");
        assertThatThrownBy(() -> new OpenAlexImporter(http, 4, "2023-01-01", "").fetch())
                .isInstanceOf(IOException.class).hasMessageContaining("429");
    }

    // ---------- CORDIS ----------

    private static final String CORDIS = """
            {"hits":{"hit":[
              {"project":{"id":"101000001","acronym":"SMARTGMINA","title":"Smart services for small municipalities",
                "objective":"The project deploys digital public services in small municipalities.",
                "status":"CLOSED","startDate":"2021-01-01","endDate":"2023-12-31","ecMaxContribution":"1500000",
                "relations":{
                  "associations":{
                    "organization":[
                      {"legalName":"UNIVERSITY OF SOMEWHERE","address":{"country":"DE","city":"BERLIN"},"@attributes":{"type":"coordinator"}},
                      {"legalName":"GMINA TYCZYN","shortName":"Tyczyn","address":{"country":"PL","city":"TYCZYN"},
                       "@attributes":{"type":"participant"},"relations":{"categories":{"category":{"code":"PUB"}}}}],
                    "programme":{"frameworkProgramme":"HORIZON","title":"Cluster 2"},
                    "result":[{"id":1},{"id":2}]},
                  "categories":{"category":[
                    {"@attributes":{"classification":"euroSciVoc"},"title":"e-government"},
                    {"@attributes":{"classification":"inna"},"title":"pominięte"}]}}}},
              {"project":{"id":"2","title":"No Polish partner","objective":"Opis.",
                "relations":{"associations":{"organization":{"legalName":"ACME","address":{"country":"FR"}}}}}},
              {"project":{"id":"3","title":"Second Polish project","objective":"Opis.",
                "relations":{"associations":{"organization":{"legalName":"GMINA LUBENIA","address":{"country":"PL"}}}}}}
            ]}}""";

    @Test
    void cordisPicksPolishOrganisationAndDescribesProjectFacts() throws Exception {
        FakeHttp http = new FakeHttp().on("cordis.europa.eu", CORDIS);

        List<SourceRecord> records = new CordisImporter(http, 1).fetch();

        // limit 1 na zapytanie: z każdego z trzech zapytań pierwszy projekt z polskim uczestnikiem
        assertThat(records).hasSize(3);
        assertThat(http.requested).hasSize(3).allSatisfy(url -> assertThat(url).contains("format=json", "num=2"));
        SourceRecord r = records.getFirst();
        assertThat(r.source()).isEqualTo("CORDIS");
        assertThat(r.ref()).isEqualTo("101000001");
        assertThat(r.title()).isEqualTo("SMARTGMINA: Smart services for small municipalities");
        assertThat(r.institution()).isEqualTo("Gmina Tyczyn");
        assertThat(r.institutionType()).isEqualTo(InstitutionType.JST);
        assertThat(r.city()).isEqualTo("Tyczyn");
        assertThat(r.year()).isEqualTo(2021);
        assertThat(r.keywords()).containsExactly("e-government");
        assertThat(r.url()).isEqualTo("https://cordis.europa.eu/project/id/101000001");
        assertThat(r.maturityHint()).isEqualTo(Maturity.PILOTAZ);
        // gmina w konsorcjum → zapytanie „samorządy” przypisuje obszar administracyjny
        assertThat(r.typeHint()).isEqualTo(InnovationType.PROCESOWA);
        assertThat(r.sectorHint()).isEqualTo(Sector.ADMINISTRACJA);
        assertThat(r.facts()).containsExactly("Status projektu: zakończony", "Okres realizacji: 2021-01-01 – 2023-12-31",
                "Program: Horyzont Europa – Cluster 2", "Dofinansowanie UE: 1 500 000 €",
                "Polska organizacja w konsorcjum: Gmina Tyczyn", "Opublikowane wyniki projektu w CORDIS: 2");
        // zapytanie „szkoły”: w projekcie nie ma szkoły, więc typ i sektor zostają do rozpoznania z treści
        assertThat(records.get(1).typeHint()).isNull();
        assertThat(records.get(1).sectorHint()).isNull();
        // zapytanie technologiczne nie wymaga konkretnego rodzaju organizacji
        assertThat(records.get(2).typeHint()).isEqualTo(InnovationType.TECHNICZNA);
    }

    @Test
    void cordisHandlesSingleHitAndRecognisesSchoolsAndCompanies() throws Exception {
        String single = """
                {"hits":{"hit":{"project":{"id":"7","title":"School robotics","objective":"Robotics classes.","status":"SIGNED",
                  "relations":{"associations":{"organization":[
                    {"legalName":"QSAR LAB SPOLKA Z OGRANICZONA ODPOWIEDZIALNOSCIA","address":{"country":"PL"},
                     "relations":{"categories":{"category":{"code":"PRC"}}}},
                    {"legalName":"I LICEUM OGOLNOKSZTALCACE W RZESZOWIE","address":{"country":"PL","city":"RZESZOW"},
                     "@attributes":{"type":"coordinator"},"relations":{"categories":{"category":{"code":"OTH"}}}}]}}}}}}""";
        List<SourceRecord> records = new CordisImporter(new FakeHttp().on("cordis.europa.eu", single), 5).fetch();

        assertThat(records).hasSize(3);
        // zapytanie „samorządy”: brak gminy, więc bierzemy polskiego koordynatora
        assertThat(records.getFirst().institution()).isEqualTo("I Liceum Ogolnoksztalcace w Rzeszowie");
        assertThat(records.getFirst().institutionType()).isEqualTo(InstitutionType.SZKOLA);
        assertThat(records.getFirst().maturityHint()).isEqualTo(Maturity.TESTY);
        assertThat(records.getFirst().title()).isEqualTo("School robotics");
        assertThat(records.getFirst().year()).isNull();
        assertThat(records.getFirst().facts()).contains("Status projektu: w realizacji");
        // zapytanie „szkoły” dopasowuje liceum i przypisuje obszar edukacyjny
        assertThat(records.get(1).typeHint()).isEqualTo(InnovationType.EDUKACYJNA);
        assertThat(records.get(1).sectorHint()).isEqualTo(Sector.EDUKACJA);
    }

    @Test
    void cordisDescribesItselfAndFormatsFacts() {
        CordisImporter importer = new CordisImporter(new FakeHttp(), 8);
        assertThat(importer.name()).isEqualTo("CORDIS");
        assertThat(importer.live()).isTrue();
        assertThat(importer.homepage()).isEqualTo("https://cordis.europa.eu");
        assertThat(importer.description()).isNotBlank();
        assertThat(CordisImporter.statusLabel("TERMINATED")).isEqualTo("przerwany");
        assertThat(CordisImporter.statusLabel("")).isEqualTo("brak informacji");
        assertThat(CordisImporter.statusLabel("ONGOING")).isEqualTo("ongoing");
        assertThat(CordisImporter.euro("brak")).isEqualTo("brak €");
    }

    // ---------- RAD-on ----------

    private static String radonProduct(String number, String published, String typeCode) {
        return """
                {"protectionTypeCode":"%s","protectionType":"Patent na wynalazek","grantingInstitutionCode":"3",
                 "applicationNumber":"%s","publicationDate":"%s","protectionUuid":"UUID-%s",
                 "protectionTitle":"Urządzenie nr %s",
                 "productDescription":"Przedmiotem zgłoszenia jest urządzenie do pomiaru drgań konstrukcji mostowych z bezprzewodową transmisją danych.",
                 "relatedInstitutions":[{"institutionName":"Politechnika Rzeszowska im. Ignacego Łukasiewicza"}]}"""
                .formatted(typeCode, number, published, number, number);
    }

    private static final String RADON_PRODUCTS = "{\"results\":[" + radonProduct("435372", "2023-11-20", "1") + ","
            + radonProduct("440001", "2025-02-01", "1") + "," + radonProduct("450000", "2025-06-01", "3") + "]}";

    private static final String RADON_INSTITUTIONS = """
            {"results":[{"name":"Politechnika Rzeszowska im. Ignacego Łukasiewicza",
                         "addresses":[{"city":"Rzeszów","voivodeship":"podkarpackie"}]}]}""";

    @Test
    void radonTakesNewestPatentsPerQueryWithoutDuplicates() throws Exception {
        FakeHttp http = new FakeHttp().on("/products", RADON_PRODUCTS).on("/institutions", RADON_INSTITUTIONS);
        RadonImporter importer = new RadonImporter(http, 1, "2023-01-01");

        List<SourceRecord> records = importer.fetch();

        // limit 1 na zapytanie: najpierw najnowszy patent, kolejne zapytanie dobiera następny, odmiana rośliny odpada
        assertThat(records).extracting(SourceRecord::ref).containsExactly("P.440001", "P.435372");
        assertThat(records).allSatisfy(r -> {
            assertThat(r.city()).isEqualTo("Rzeszów");
            assertThat(r.region()).isEqualTo("podkarpackie");
        });
        List<String> products = http.requested.stream().filter(u -> u.contains("/products")).toList();
        assertThat(products).hasSize(RadonImporter.REGIONAL.size() + 1);
        assertThat(products.getFirst()).contains("institutionName=Politechnika+Rzeszowska", "publicationDateFrom=2023-01-01",
                "resultNumbers=100");
        assertThat(products.getLast()).doesNotContain("institutionName");
        // siedziba instytucji jest sprawdzana w rejestrze raz, mimo dwóch rekordów
        assertThat(http.requested.stream().filter(u -> u.contains("/institutions"))).hasSize(1);
        assertThat(importer.name()).isEqualTo("RAD-on");
        assertThat(importer.live()).isTrue();
        assertThat(importer.homepage()).isEqualTo(RadonImporter.PAGE);
        assertThat(importer.description()).isNotBlank();
    }

    @Test
    void radonFallsBackToWholeCountryWhenRegisterIsUnavailable() throws Exception {
        // rejestr instytucji nie odpowiada – rekord i tak powstaje, tylko bez miasta i województwa
        FakeHttp http = new FakeHttp().on("/products", RADON_PRODUCTS);

        List<SourceRecord> records = new RadonImporter(http, 5, "2023-01-01").fetch();

        assertThat(records).hasSize(2).allSatisfy(r -> {
            assertThat(r.city()).isNull();
            assertThat(r.region()).isEqualTo("Polska");
        });
    }

    // ---------- BZP ----------

    private static String notice(String ref, String object, String buyer, Instant published, Instant deadline) {
        return """
                {"bzpNumber":"%s","orderObject":"%s","organizationName":"%s","organizationCity":"Lubenia",
                 "organizationProvince":"PL18","cpvCode":"48000000-8 (Pakiety oprogramowania i systemy informatyczne)",
                 "publicationDate":"%s","submittingOffersDate":"%s","objectId":"id-%s","htmlBody":""}"""
                .formatted(ref, object, buyer, published, deadline, ref.replaceAll("\\W", ""));
    }

    @Test
    void bzpKeepsNewestOpenNoticeOfEachOrder() throws Exception {
        Instant now = Instant.now();
        Instant open = now.plus(Duration.ofDays(5));
        String notices = "[" + String.join(",",
                notice("2026/BZP 1", "Wdrożenie platformy e-usług", "GMINA LUBENIA", now.minus(Duration.ofDays(3)), open),
                // to samo zamówienie ogłoszone ponownie – zostaje nowsze ogłoszenie
                notice("2026/BZP 2", "Wdrożenie platformy e-usług (2)", "GMINA LUBENIA", now.minus(Duration.ofDays(1)), open),
                notice("2026/BZP 3", "Wdrożenie systemu obiegu dokumentów", "GMINA TYCZYN", now.minus(Duration.ofDays(9)),
                        now.minus(Duration.ofDays(1))),
                notice("2026/BZP 4", "Serwis systemu finansowego", "GMINA TYCZYN", now.minus(Duration.ofDays(2)), open)) + "]";
        FakeHttp http = new FakeHttp().on("ezamowienia.gov.pl", notices);
        BzpNeedsImporter importer = new BzpNeedsImporter(http, 6, 30, 7);

        List<NeedRecord> needs = importer.fetch();

        assertThat(needs).extracting(NeedRecord::ref).containsExactly("2026/BZP 2");
        assertThat(needs.getFirst().institution()).isEqualTo("Gmina Lubenia");
        // cztery zapytania dla Podkarpacia, potem te same dla całego kraju
        assertThat(http.requested).hasSize(8);
        assertThat(http.requested.subList(0, 4)).allSatisfy(u -> assertThat(u).contains("OrganizationProvince=PL18"));
        assertThat(http.requested.subList(4, 8)).allSatisfy(u -> assertThat(u).doesNotContain("OrganizationProvince"));
        assertThat(http.requested).allSatisfy(u -> assertThat(u).contains("NoticeType=ContractNotice", "PageSize=40"));
        assertThat(http.requested).anySatisfy(u -> assertThat(u).contains("CpvCode=73000000-2"));
        assertThat(importer.name()).isEqualTo("BZP");
        assertThat(importer.live()).isTrue();
        assertThat(importer.homepage()).contains("ezamowienia.gov.pl");
        assertThat(importer.description()).isNotBlank();
    }

    @Test
    void bzpLimitsNeedsPerQuery() throws Exception {
        Instant now = Instant.now();
        Instant open = now.plus(Duration.ofDays(5));
        String notices = "[" + String.join(",",
                notice("2026/BZP 10", "Wdrożenie systemu A", "GMINA A", now.minus(Duration.ofDays(3)), open),
                notice("2026/BZP 11", "Wdrożenie systemu B", "GMINA B", now.minus(Duration.ofDays(2)), open),
                notice("2026/BZP 12", "Wdrożenie systemu C", "GMINA C", now.minus(Duration.ofDays(1)), open)) + "]";

        List<NeedRecord> needs = new BzpNeedsImporter(new FakeHttp().on("ezamowienia.gov.pl", notices), 1, 30, 7).fetch();

        // limit 1 na zapytanie: każde kolejne zapytanie dokłada następne najnowsze, jeszcze niewybrane ogłoszenie
        assertThat(needs).extracting(NeedRecord::ref).containsExactly("2026/BZP 12", "2026/BZP 11", "2026/BZP 10");
    }
}
