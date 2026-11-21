package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.Area;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Sector;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Parsowanie patentów z RAD-on i ogłoszeń BZP – na próbkach w kształcie odpowiedzi API, bez internetu. */
class OpenSourcesParsingTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final BzpNeedsImporter.Query BY_TITLE = new BzpNeedsImporter.Query("OrderObject=x", true, true);
    private static final BzpNeedsImporter.Query RESEARCH = new BzpNeedsImporter.Query("CpvCode=73000000-2", false, false);

    private final JsonMapper json = JsonMapper.builder().build();

    private JsonNode patent(String typeCode, String office, String applicationNumber) {
        return json.readTree("""
                {"protectionTypeCode":"%s","protectionType":"Patent na wynalazek","grantingInstitutionCode":"%s",
                 "grantingInstitutionName":"Urząd Patentowy Rzeczpospolitej Polskiej","applicationNumber":"%s",
                 "applicationDate":"2020-09-21","publicationNumber":"244026","publicationDate":"2023-11-20",
                 "protectionRegion":"Polska","protectionUuid":"0A35E04B","protectionTitle":"Tytuł zapasowy",
                 "productTitles":[{"code":"angielski","value":"Device for processing plastic bottles"},
                                  {"code":"polski","value":"Urządzenie do przetwarzania butelek z tworzyw sztucznych"}],
                 "productDescription":"Przedmiotem zgłoszenia jest urządzenie i sposób do przetwarzania butelek z tworzyw sztucznych, z przeznaczeniem do ponownego wykorzystania w przemyśle budowlanym.",
                 "relatedInstitutions":[{"institutionName":"Politechnika Rzeszowska im. Ignacego Łukasiewicza"},
                                        {"institutionName":"Uniwersytet Rzeszowski"}],
                 "inventors":[{"persons":[{"firstName":"Anna","lastName":"Kowal"},{"firstName":"Jan","lastName":"Nowak"},
                                          {"firstName":"Ewa","lastName":"Lis"},{"firstName":"Adam","lastName":"Mazur"}]}]}
                """.formatted(typeCode, office, applicationNumber));
    }

    @Test
    void turnsRadonPatentIntoRadarRecord() {
        SourceRecord r = RadonImporter.toRecord(patent("1", "3", "435372"), new RadonImporter.Place("Rzeszów", "podkarpackie"));

        assertThat(r.source()).isEqualTo("RAD-on");
        assertThat(r.ref()).isEqualTo("P.435372");
        assertThat(r.title()).isEqualTo("Urządzenie do przetwarzania butelek z tworzyw sztucznych");
        assertThat(r.abstractText()).startsWith("Przedmiotem zgłoszenia jest urządzenie");
        assertThat(r.institution()).isEqualTo("Politechnika Rzeszowska im. Ignacego Łukasiewicza");
        assertThat(r.institutionType()).isEqualTo(InstitutionType.UCZELNIA);
        assertThat(r.city()).isEqualTo("Rzeszów");
        assertThat(r.region()).isEqualTo("podkarpackie");
        assertThat(r.authors()).isEqualTo("Anna Kowal, Jan Nowak, Ewa Lis i in.");
        assertThat(r.year()).isEqualTo(2023);
        assertThat(r.areaHint()).isEqualTo(Area.TECHNOLOGICZNY);
        assertThat(r.url()).isEqualTo("https://ewyszukiwarka.pue.uprp.gov.pl/search/pwp-details/P.435372");
        assertThat(r.facts()).contains("Rodzaj ochrony: Patent na wynalazek", "Numer zgłoszenia: 435372 (z 2020-09-21)",
                "Numer publikacji: 244026", "Data publikacji: 2023-11-20",
                "Instytucje powiązane: Politechnika Rzeszowska im. Ignacego Łukasiewicza, Uniwersytet Rzeszowski");
    }

    @Test
    void linksOnlyPolishPatentOfficeRecordsToUprp() {
        assertThat(RadonImporter.ref(patent("2", "3", "132239"))).isEqualTo("W.132239");
        // zgłoszenie w urzędzie zagranicznym: numer nie jest numerem UPRP, więc bez linku do e-Wyszukiwarki
        JsonNode foreign = patent("1", "1", "43249320");
        assertThat(RadonImporter.ref(foreign)).isEqualTo("43249320");
        assertThat(RadonImporter.ref(patent("1", "1", ""))).isEqualTo("0A35E04B");
        assertThat(RadonImporter.url(foreign)).isEqualTo(RadonImporter.PAGE);
    }

    @Test
    void skipsRadonRecordsThatCannotBecomeACard() {
        assertThat(RadonImporter.usable(patent("1", "3", "435372"))).isTrue();
        // wyłączne prawo hodowcy do odmiany rośliny – sama nazwa botaniczna, bez opisu rozwiązania
        assertThat(RadonImporter.usable(patent("3", "3", "435372"))).isFalse();
        assertThat(RadonImporter.usable(json.readTree(
                "{\"protectionTypeCode\":\"1\",\"protectionTitle\":\"Katalizator\",\"productDescription\":\"za krótki\"}"))).isFalse();
    }

    @Test
    void readsInstitutionSeatFromRadonRegister() {
        JsonNode register = json.readTree("""
                [{"name":"Politechnika Warszawska","addresses":[{"city":"Warszawa","voivodeship":"mazowieckie"}]},
                 {"name":"Politechnika Rzeszowska im. Ignacego Łukasiewicza","addresses":[{"city":"Rzeszów","voivodeship":"Podkarpackie"}]}]
                """);
        assertThat(RadonImporter.place(register, "politechnika rzeszowska im. ignacego łukasiewicza"))
                .isEqualTo(new RadonImporter.Place("Rzeszów", "podkarpackie"));
        assertThat(RadonImporter.place(register, "Uczelnia spoza rejestru")).isEqualTo(RadonImporter.Place.UNKNOWN);
    }

    private JsonNode notice(String object, String buyer, String cpv, String deadline) {
        return json.readTree("""
                {"noticeType":"ContractNotice","bzpNumber":"2026/BZP 00517343","orderObject":%s,"cpvCode":%s,
                 "publicationDate":"2026-11-02T19:12:43.8337484Z","submittingOffersDate":"%s",
                 "organizationName":%s,"organizationCity":"Lubenia","organizationProvince":"PL18",
                 "objectId":"08df3915-45d3-300d-e2d6-6b000120cdf0",
                 "htmlBody":"<style>h3 { font-size: 11pt }</style><h3>4.2.2.) Krótki opis przedmiotu zamówienia</h3><p>Dostawa i&nbsp;uruchomienie platformy e-usług dla mieszkańców gminy &quot;Lubenia&quot;.</p><h3>4.2.6.) Główny kod CPV: 48000000-8</h3><p>dalsza treść</p>"}
                """.formatted(json.writeValueAsString(object), json.writeValueAsString(cpv), deadline, json.writeValueAsString(buyer)));
    }

    @Test
    void turnsBzpNoticeIntoNeed() {
        NeedRecord r = BzpNeedsImporter.toRecord(notice("Wdrożenie platformy e-usług publicznych w Gminie Lubenia",
                "GMINA LUBENIA", "48000000-8 (Pakiety oprogramowania i systemy informatyczne),30200000-1 (Urządzenia komputerowe)",
                "2026-11-25T09:00:00Z"), BY_TITLE, NOW);

        assertThat(r.source()).isEqualTo("BZP");
        assertThat(r.ref()).isEqualTo("2026/BZP 00517343");
        assertThat(r.title()).isEqualTo("Wdrożenie platformy e-usług publicznych w Gminie Lubenia");
        assertThat(r.description()).isEqualTo("Dostawa i uruchomienie platformy e-usług dla mieszkańców gminy \"Lubenia\".");
        assertThat(r.institution()).isEqualTo("Gmina Lubenia");
        assertThat(r.institutionType()).isEqualTo(InstitutionType.JST);
        assertThat(r.city()).isEqualTo("Lubenia");
        assertThat(r.region()).isEqualTo("podkarpackie");
        assertThat(r.sector()).isEqualTo(Sector.ADMINISTRACJA);
        assertThat(r.tags()).containsExactly("pakiety oprogramowania i systemy informatyczne", "urządzenia komputerowe");
        assertThat(r.url()).isEqualTo("https://ezamowienia.gov.pl/mo-client-board/bzp/notice-details/id/08df3915-45d3-300d-e2d6-6b000120cdf0");
        assertThat(r.publishedAt()).isEqualTo(Instant.parse("2026-11-02T19:12:43.8337484Z"));
        assertThat(r.deadline()).isEqualTo(Instant.parse("2026-11-25T09:00:00Z"));
        assertThat(r.wantsReadySolution()).isTrue();
    }

    @Test
    void skipsBzpNoticesThatAreNotOpenNeeds() {
        String cpv = "72000000-5 (Usługi informatyczne: konsultacyjne, opracowywania oprogramowania, internetowe i wsparcia)";
        // termin składania ofert minął
        assertThat(BzpNeedsImporter.toRecord(notice("Wdrożenie systemu obiegu dokumentów", "GMINA LUBENIA", cpv,
                "2026-11-03T09:00:00Z"), BY_TITLE, NOW)).isNull();
        // utrzymanie istniejącego rozwiązania to nie zapotrzebowanie na nowe
        assertThat(BzpNeedsImporter.toRecord(notice("Nadzór autorski i serwis oprogramowania aplikacyjnego AMMS",
                "Szpital Wojewódzki", cpv, "2026-11-25T09:00:00Z"), BY_TITLE, NOW)).isNull();
        assertThat(BzpNeedsImporter.toRecord(notice("Dostawa subskrypcji licencji oprogramowania biurowego",
                "Uniwersytet Rzeszowski", cpv, "2026-11-25T09:00:00Z"), BY_TITLE, NOW)).isNull();
        assertThat(BzpNeedsImporter.toRecord(notice("Serwis i rozwój systemu SAP", "Centrum Usług Informatycznych", cpv,
                "2026-11-25T09:00:00Z"), BY_TITLE, NOW)).isNull();
        // sam sprzęt, nawet „z wdrożeniem”
        assertThat(BzpNeedsImporter.toRecord(notice("Dostawa i wdrożenie sprzętu serwerowego i pamięci masowych",
                "Wojskowy Instytut Techniczny", cpv, "2026-11-25T09:00:00Z"), BY_TITLE, NOW)).isNull();
        // budowa serwisu internetowego to nie „serwis” istniejącego systemu
        assertThat(BzpNeedsImporter.toRecord(notice("Zaprojektowanie i wdrożenie serwisu internetowego gminy",
                "GMINA LUBENIA", cpv, "2026-11-25T09:00:00Z"), BY_TITLE, NOW)).isNotNull();
        // w nazwie nic nie wskazuje na nowe rozwiązanie
        assertThat(BzpNeedsImporter.toRecord(notice("Zimowe utrzymanie dróg gminnych", "GMINA LUBENIA", cpv,
                "2026-11-25T09:00:00Z"), BY_TITLE, NOW)).isNull();
    }

    @Test
    void skipsBzpNoticesWhoseDescriptionStartsWithLicencePurchase() {
        JsonNode n = json.readTree("""
                {"bzpNumber":"2026/BZP 00000002","orderObject":"Dostawa oprogramowania dla ośrodka","organizationName":"Ośrodek Doradztwa",
                 "cpvCode":"48000000-8 (Pakiety oprogramowania i systemy informatyczne)","submittingOffersDate":"2026-11-25T09:00:00Z",
                 "htmlBody":"<h3>4.2.2.) Krótki opis przedmiotu zamówienia</h3><p>Dostawa subskrypcji i wsparcia oprogramowania ERP</p><h3>4.2.6.) Główny kod CPV</h3>"}
                """);
        assertThat(BzpNeedsImporter.toRecord(n, RESEARCH, NOW)).isNull();
        // licencja wymieniona dalej jako jedna z części większego wdrożenia nie wyklucza ogłoszenia
        JsonNode part = json.readTree(json.writeValueAsString(n).replace("Dostawa subskrypcji i wsparcia oprogramowania ERP",
                "Część 1 - Zakup programu do zarządzania zasobami IT. Licencja na 50 stanowisk. Część 2 - wdrożenie platformy e-usług."));
        assertThat(BzpNeedsImporter.toRecord(part, RESEARCH, NOW)).isNotNull();
    }

    @Test
    void researchServicesAreNeedsForEarlyStageSolutions() {
        NeedRecord r = BzpNeedsImporter.toRecord(notice("Usługi doradcze w zakresie projektowania strukturalnego satelity",
                "Centrum Badań Kosmicznych Polskiej Akademii Nauk", "73000000-2 (Usługi badawcze i eksperymentalno-rozwojowe oraz pokrewne usługi doradcze)",
                "2026-11-25T09:00:00Z"), RESEARCH, NOW);

        assertThat(r).isNotNull();
        assertThat(r.wantsReadySolution()).isFalse();
        assertThat(r.sector()).isNull();
        // nazwa kodu CPV dłuższa niż 60 znaków nie nadaje się na tag
        assertThat(r.tags()).isEmpty();
    }

    @Test
    void parsesCpvCodesWithCommasAndBracketsInNames() {
        assertThat(BzpNeedsImporter.cpv("30233141-1 (Nadmiarowa macierz niezależnych dysków (RAID)),"
                + "72000000-5 (Usługi informatyczne: konsultacyjne, opracowywania oprogramowania, internetowe i wsparcia),"
                + "48820000-2 (Serwery)"))
                .containsExactly(
                        org.assertj.core.api.Assertions.entry("30233141", "Nadmiarowa macierz niezależnych dysków (RAID)"),
                        org.assertj.core.api.Assertions.entry("72000000", "Usługi informatyczne: konsultacyjne, opracowywania oprogramowania, internetowe i wsparcia"),
                        org.assertj.core.api.Assertions.entry("48820000", "Serwery"));
    }

    @Test
    void derivesNeedSectorFromBuyerAndCpv() {
        assertThat(BzpNeedsImporter.sector(List.of("48000000"), "Wojewódzki Szpital im. Zofii z Zamoyskich", InstitutionType.INSTYTUCJA_PUBLICZNA))
                .isEqualTo(Sector.ZDROWIE);
        assertThat(BzpNeedsImporter.sector(List.of("72000000"), "Zespół Szkół nr 2", InstitutionType.SZKOLA)).isEqualTo(Sector.EDUKACJA);
        assertThat(BzpNeedsImporter.sector(List.of("90500000", "72000000"), "Gmina Pruchnik", InstitutionType.JST)).isEqualTo(Sector.SRODOWISKO);
        assertThat(BzpNeedsImporter.sector(List.of("72000000"), "Sąd Okręgowy w Rzeszowie", InstitutionType.INSTYTUCJA_PUBLICZNA))
                .isEqualTo(Sector.ICT);
        assertThat(BzpNeedsImporter.sector(List.of("45000000"), "Sąd Okręgowy w Rzeszowie", InstitutionType.INSTYTUCJA_PUBLICZNA)).isNull();
        // szkolenie personelu dołączone do dostawy oprogramowania nie zmienia sektora na edukację
        assertThat(BzpNeedsImporter.sector(List.of("80511000", "48000000"), "Biuro Rozwoju Gdańska", InstitutionType.INSTYTUCJA_PUBLICZNA))
                .isEqualTo(Sector.ICT);
        assertThat(BzpNeedsImporter.sector(List.of("80500000"), "Biuro Rozwoju Gdańska", InstitutionType.INSTYTUCJA_PUBLICZNA))
                .isEqualTo(Sector.EDUKACJA);
    }

    @Test
    void tidiesPolishCompanyNamesFromBzp() {
        assertThat(Names.titleCase("PRZEDSIĘBIORSTWO USŁUG KOMUNALNYCH SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ"))
                .isEqualTo("Przedsiębiorstwo Usług Komunalnych sp. z o.o.");
        assertThat(Names.titleCase("REGIONALNE CENTRUM NAUKOWO-TECHNOLOGICZNE")).isEqualTo("Regionalne Centrum Naukowo-Technologiczne");
        assertThat(Names.institutionType("Miejskie Wodociągi i Kanalizacja Sp. z o. o.", InstitutionType.INSTYTUCJA_PUBLICZNA))
                .isEqualTo(InstitutionType.FIRMA);
    }
}
