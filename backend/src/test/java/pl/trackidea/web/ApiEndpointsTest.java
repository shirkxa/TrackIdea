package pl.trackidea.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.Institution;
import pl.trackidea.domain.Need;
import pl.trackidea.domain.Person;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.NeedRepository;
import pl.trackidea.repo.PersonRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpointy REST na pełnym kontekście aplikacji z danymi demo: katalog, karta, potrzeby, słowniki, logowanie, panel PCI. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@AutoConfigureMockMvc
@Transactional
class ApiEndpointsTest {

    private static final String ANALYST = "k.nowak@pci.example.org";

    @Autowired
    MockMvc mvc;
    @Autowired
    InnovationRepository innovations;
    @Autowired
    NeedRepository needs;
    @Autowired
    PersonRepository people;

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ANALYST + "\",\"password\":\"demo123\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private Innovation first(InnovationStatus status) {
        return innovations.findByStatusIn(List.of(status)).getFirst();
    }

    // ---------- katalog ----------

    @Test
    void catalogFiltersByAreaTypeSectorAndInstitution() throws Exception {
        mvc.perform(get("/api/innovations").param("area", "EDUKACYJNY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[*].area.value", everyItem(is("EDUKACYJNY"))));
        mvc.perform(get("/api/innovations").param("type", "TECHNICZNA").param("sector", "ICT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type.value", everyItem(is("TECHNICZNA"))))
                .andExpect(jsonPath("$[*].sector.value", everyItem(is("ICT"))));
        mvc.perform(get("/api/innovations").param("maturity", "STOSOWANE").param("institutionType", "SZKOLA").param("minTrl", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].maturity.value", everyItem(is("STOSOWANE"))))
                .andExpect(jsonPath("$[*].institution.type.value", everyItem(is("SZKOLA"))))
                .andExpect(jsonPath("$[*].trl.level", everyItem(greaterThanOrEqualTo(5))));

        Innovation any = first(InnovationStatus.OCENIONA);
        Institution inst = any.getInstitution();
        mvc.perform(get("/api/innovations").param("institutionId", String.valueOf(inst.getId())))
                .andExpect(jsonPath("$[*].institution.id", everyItem(is(inst.getId().intValue()))))
                .andExpect(jsonPath("$[*].id", hasItem(any.getId().intValue())));
        // nazwa instytucji bez wielkości liter i polskich znaków
        String needle = TextVectorizerAccess.plain(inst.getName()).toUpperCase();
        mvc.perform(get("/api/innovations").param("institution", "  " + needle + " "))
                .andExpect(jsonPath("$[*].id", hasItem(any.getId().intValue())));
        mvc.perform(get("/api/innovations").param("transferModel", "nieznany")).andExpect(status().is4xxClientError());
    }

    @Test
    void catalogSearchesAndSorts() throws Exception {
        Innovation any = first(InnovationStatus.OCENIONA);
        mvc.perform(get("/api/innovations").param("q", any.getTitle()))
                .andExpect(status().isOk())
                // trafienie po tytule jest pierwsze: bez podanego sortowania wyniki idą od najtrafniejszych
                .andExpect(jsonPath("$[0].id").value(any.getId()));
        mvc.perform(get("/api/innovations").param("q", any.getTitle()).param("sort", "relevance"))
                .andExpect(jsonPath("$[0].id").value(any.getId()));
        mvc.perform(get("/api/innovations").param("q", "qwertyzxcv niemaTakiegoSlowa")).andExpect(jsonPath("$", hasSize(0)));
        for (String sort : new String[]{"score", "updated", "trl", "newest"}) {
            mvc.perform(get("/api/innovations").param("sort", sort))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(greaterThan(5))));
        }
        MvcResult byScore = mvc.perform(get("/api/innovations").param("sort", "score").param("status", "OCENIONA")).andReturn();
        List<Number> scores = com.jayway.jsonpath.JsonPath.read(byScore.getResponse().getContentAsString(StandardCharsets.UTF_8), "$[*].score");
        assertThat(scores).extracting(Number::doubleValue).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void catalogExportsCsvForSpreadsheets() throws Exception {
        MvcResult result = mvc.perform(get("/api/innovations/export").param("status", "OCENIONA"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"trackidea-innowacje-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // BOM i średnik – plik otwiera się wprost w Excelu
        assertThat(csv).startsWith("﻿ID;Tytuł;Instytucja;");
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(innovations.findByStatusIn(List.of(InnovationStatus.OCENIONA)).size() + 1);
        assertThat(lines[1]).contains("\"Zweryfikowana\"", "/innovations/");
    }

    // ---------- karta innowacji ----------

    @Test
    void cardShowsDetailsSimilarRecommendationsAndMatches() throws Exception {
        Innovation inn = first(InnovationStatus.OCENIONA);
        mvc.perform(get("/api/innovations/" + inn.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.id").value(inn.getId()))
                .andExpect(jsonPath("$.card.title").value(inn.getTitle()))
                .andExpect(jsonPath("$.completeness.percent").isNumber())
                .andExpect(jsonPath("$.assessment.approved").value(true))
                .andExpect(jsonPath("$.events").isArray())
                // gość niczego nie może zmieniać
                .andExpect(jsonPath("$.viewer.canEdit").value(false));
        mvc.perform(get("/api/innovations/" + inn.getId() + "/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.innovations").isArray())
                .andExpect(jsonPath("$.priorArt").isArray());
        mvc.perform(get("/api/innovations/" + inn.getId() + "/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].path").isString())
                .andExpect(jsonPath("$[0].nextSteps").isArray());
        mvc.perform(get("/api/innovations/" + inn.getId() + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needs").isArray());
        mvc.perform(get("/api/innovations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Nie ma innowacji")));
    }

    @Test
    void analystEditsAssessesAndAssignsPaths() throws Exception {
        Innovation inn = first(InnovationStatus.WYKRYTA);
        String form = """
                {"title":"Karta po poprawce analityka","summary":"Krótki opis po weryfikacji.","type":"PROCESOWA","sector":"ADMINISTRACJA",
                 "maturity":"PILOTAZ","trl":6,"area":"ADMINISTRACYJNY","tags":["E-Usługi"],"links":["https://example.org/zrodlo"]}""";
        mvc.perform(put("/api/innovations/" + inn.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isUnauthorized());

        MockHttpSession analyst = login();
        mvc.perform(put("/api/innovations/" + inn.getId()).session(analyst).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.title").value("Karta po poprawce analityka"))
                .andExpect(jsonPath("$.card.tags[0]").value("e-usługi"))
                .andExpect(jsonPath("$.card.trl.level").value(6))
                .andExpect(jsonPath("$.viewer.canEdit").value(true));
        mvc.perform(put("/api/innovations/" + inn.getId()).session(analyst).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"trl\":12}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("TRL")));

        mvc.perform(post("/api/innovations/" + inn.getId() + "/propose-assessment").session(analyst).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.status.value").value("W_OCENIE"));
        mvc.perform(post("/api/innovations/" + inn.getId() + "/paths").session(analyst).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paths\":[\"ROZWOJ_PCI\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/innovations/" + inn.getId() + "/assessment/approve").session(analyst).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scores\":[{\"criterion\":\"WIARYGODNOSC\",\"score\":4,\"justification\":\"Sprawdzone w źródle\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.status.value").value("OCENIONA"))
                .andExpect(jsonPath("$.card.scoreEstimated").value(false))
                .andExpect(jsonPath("$.assessment.approvedBy.name").isString());
        mvc.perform(post("/api/innovations/" + inn.getId() + "/paths").session(analyst).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paths\":[\"OTWARTA_REPLIKACJA\",\"PILOTAZ_BR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chosenPaths[*].value", hasItem("PILOTAZ_BR")));
    }

    @Test
    void rejectedCardIsHiddenFromGuests() throws Exception {
        Innovation inn = first(InnovationStatus.WYKRYTA);
        MockHttpSession analyst = login();

        mvc.perform(post("/api/innovations/" + inn.getId() + "/reject").session(analyst).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Zwykły zakup sprzętu.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.status.value").value("ODRZUCONA"));

        mvc.perform(get("/api/innovations/" + inn.getId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("wyłączony z katalogu")));
        mvc.perform(get("/api/innovations/" + inn.getId()).session(analyst)).andExpect(status().isOk());
        mvc.perform(get("/api/innovations/compare").param("ids", String.valueOf(inn.getId()))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/innovations/compare").param("ids", String.valueOf(inn.getId())).session(analyst))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/innovations")).andExpect(jsonPath("$[?(@.id == " + inn.getId() + ")]", hasSize(0)));
    }

    // ---------- potrzeby ----------

    @Test
    void needsBoardListsNeedsWithMatches() throws Exception {
        Need need = needs.findByOpenTrue().getFirst();
        mvc.perform(get("/api/needs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(needs.findByOpenTrue().size())))
                .andExpect(jsonPath("$[0].need.title").isString())
                .andExpect(jsonPath("$[0].matches").isArray());
        mvc.perform(get("/api/needs/" + need.getId() + "/matches")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/needs/999999/matches")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Brak potrzeby."));
    }

    @Test
    void loggedInUserAddsNeed() throws Exception {
        String body = """
                {"title":"  Kolejki w wydziale komunikacji  ","description":"Mieszkańcy czekają ponad godzinę na rejestrację pojazdu.",
                 "sector":"ADMINISTRACJA","tags":[" Kolejki ",""," Urząd"],"wantsReadySolution":false}""";
        mvc.perform(post("/api/needs").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        MockHttpSession analyst = login();
        mvc.perform(post("/api/needs").session(analyst).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.need.title").value("Kolejki w wydziale komunikacji"))
                .andExpect(jsonPath("$.need.tags", hasSize(2)))
                .andExpect(jsonPath("$.need.tags", hasItem("kolejki")))
                .andExpect(jsonPath("$.need.wantsReadySolution").value(false))
                .andExpect(jsonPath("$.need.open").value(true))
                // potrzeba z formularza nie ma źródła zewnętrznego i jest przypisana do instytucji zgłaszającego
                .andExpect(jsonPath("$.need.source").doesNotExist())
                .andExpect(jsonPath("$.need.institution.name").isString())
                .andExpect(jsonPath("$.matches").isArray());
        mvc.perform(post("/api/needs").session(analyst).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Podaj tytuł potrzeby."));
        // brak pola wantsReadySolution = szukają gotowego rozwiązania
        mvc.perform(post("/api/needs").session(analyst).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Monitoring zużycia wody\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.need.wantsReadySolution").value(true));
    }

    @Test
    void similarityEndpointComparesAnyText() throws Exception {
        Innovation inn = first(InnovationStatus.OCENIONA);
        String body = "{\"text\":" + new tools.jackson.databind.json.JsonMapper().writeValueAsString(inn.searchText()) + "}";
        mvc.perform(post("/api/similar").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        MockHttpSession analyst = login();
        mvc.perform(post("/api/similar").session(analyst).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.innovations[0].id").value(inn.getId()))
                .andExpect(jsonPath("$.priorArt").isArray());
        // pominięcie własnej karty i pusty tekst
        mvc.perform(post("/api/similar").session(analyst).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("}", ",\"excludeId\":" + inn.getId() + "}")))
                .andExpect(jsonPath("$.innovations[?(@.id == " + inn.getId() + ")]", hasSize(0)));
        mvc.perform(post("/api/similar").session(analyst).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.innovations", hasSize(0)));
    }

    // ---------- słowniki, instytucje, statystyki ----------

    @Test
    void dictionariesDescribeEnumsForFrontend() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas", hasSize(3)))
                .andExpect(jsonPath("$.areas[0].description").isString())
                .andExpect(jsonPath("$.types", hasSize(5)))
                .andExpect(jsonPath("$.maturities", hasSize(5)))
                .andExpect(jsonPath("$.paths", hasSize(5)))
                .andExpect(jsonPath("$.roles", hasSize(2)))
                .andExpect(jsonPath("$.statuses[0].value").value("WYKRYTA"))
                .andExpect(jsonPath("$.criteria", hasSize(5)))
                .andExpect(jsonPath("$.criteria[*].weight", hasItem(25)))
                .andExpect(jsonPath("$.trlLevels", hasSize(9)))
                .andExpect(jsonPath("$.trlLevels[8].level").value(9))
                .andExpect(jsonPath("$.aiEnabled").isBoolean());
    }

    @Test
    void peopleAndInstitutionsArePublicDirectories() throws Exception {
        mvc.perform(get("/api/people"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize((int) people.count())))
                // konta nie ujawniają e-maila ani hasła
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].role.value").value("EKSPERT"));
        mvc.perform(get("/api/institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(5))))
                .andExpect(jsonPath("$[0].publicSector").isBoolean());

        Innovation inn = first(InnovationStatus.OCENIONA);
        Institution inst = inn.getInstitution();
        mvc.perform(get("/api/institutions/" + inst.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institution.name").value(inst.getName()))
                .andExpect(jsonPath("$.innovations[*].id", hasItem(inn.getId().intValue())))
                .andExpect(jsonPath("$.needs").isArray());
        Need need = needs.findAll().stream().filter(n -> n.getInstitution() != null).findFirst().orElseThrow();
        mvc.perform(get("/api/institutions/" + need.getInstitution().getId()))
                .andExpect(jsonPath("$.needs[*].id", hasItem(need.getId().intValue())));
        mvc.perform(get("/api/institutions/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Brak instytucji."));
    }

    @Test
    void statsAndRadarStateFeedPciPanel() throws Exception {
        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis", hasSize(7)))
                .andExpect(jsonPath("$.kpis[0].key").value("found"))
                .andExpect(jsonPath("$.byStatus").isMap())
                .andExpect(jsonPath("$.bySource").isMap());
        mvc.perform(get("/api/radar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.sources[*].name", hasItem("BZP")))
                .andExpect(jsonPath("$.sources[*].name", hasItem("RAD-on")))
                .andExpect(jsonPath("$.sources[?(@.name == 'Próbki offline')].active", hasItem(true)))
                .andExpect(jsonPath("$.sources[?(@.name == 'BZP')].active", hasItem(false)))
                .andExpect(jsonPath("$.records", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.records[0].outcome").isString());
        mvc.perform(get("/api/pci/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toReview", hasSize(innovations.findByStatusIn(List.of(InnovationStatus.WYKRYTA)).size())))
                .andExpect(jsonPath("$.toApprove").isArray());
    }

    @Test
    void scanReportsNeedsAndInnovations() throws Exception {
        mvc.perform(post("/api/radar/scan").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/radar/scan").session(login()).with(csrf()))
                .andExpect(status().isOk())
                // próbki offline są już w bazie, a źródła sieciowe (w tym BZP) są w trybie offline wyłączone
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(greaterThan(0)))
                .andExpect(jsonPath("$.needsCreated").value(0))
                .andExpect(jsonPath("$.needsClosed").value(0))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    // ---------- logowanie ----------

    @Test
    void sessionStartsWithLoginAndEndsWithLogout() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.demoMode").value(true));

        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.email").value(ANALYST))
                .andExpect(jsonPath("$.user.role.value").value("EKSPERT"));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").doesNotExist());
        assertThat(session.isInvalid()).isTrue();
        // wylogowanie bez sesji też jest poprawne
        mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isOk());
    }

    @Test
    void demoModeSwitchesAccountsWithoutPassword() throws Exception {
        Person admin = people.findAll().stream().filter(p -> !ANALYST.equals(p.getEmail())).findFirst().orElseThrow();
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/demo/" + admin.getId()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value(admin.getName()))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.email").value(admin.getEmail()));
        // przełączenie konta w trwającej sesji
        Person analyst = people.findByEmailIgnoreCase(ANALYST).orElseThrow();
        mvc.perform(post("/api/auth/demo/" + analyst.getId()).session(session).with(csrf()))
                .andExpect(jsonPath("$.email").value(ANALYST));
        mvc.perform(post("/api/auth/demo/999999").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void invalidLoginRequestsGetReadableErrors() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Podaj e-mail i hasło."));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{to nie json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Nieprawidłowe dane formularza."));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nikt@example.org\",\"password\":\"demo123\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Nieprawidłowy e-mail lub hasło."));
    }

    /** Uproszczenie nazwy tak jak w wyszukiwarce: bez polskich znaków. */
    private static final class TextVectorizerAccess {
        static String plain(String s) {
            return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('ł', 'l').replace('Ł', 'L');
        }
    }
}
