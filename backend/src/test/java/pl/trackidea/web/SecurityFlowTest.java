package pl.trackidea.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.repo.InnovationRepository;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Logowanie, CSRF i uprawnienia zespołu PCI – na pełnym kontekście aplikacji z danymi demo. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@AutoConfigureMockMvc
class SecurityFlowTest {

    private static final String ANALYST = "k.nowak@pci.example.org";

    @Autowired
    MockMvc mvc;

    @Autowired
    InnovationRepository innovations;

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private Long firstWithStatus(InnovationStatus status) {
        return innovations.findByStatusIn(List.of(status)).getFirst().getId();
    }

    @Test
    void catalogIsPublicButChangesRequireLogin() throws Exception {
        mvc.perform(get("/api/innovations")).andExpect(status().isOk());
        mvc.perform(post("/api/innovations/" + firstWithStatus(InnovationStatus.WYKRYTA) + "/propose-assessment")
                .with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void everyDetectedCardHasAutomaticEstimatedScore() throws Exception {
        // FAQ: Radar sam ocenia potencjał – wynik jest od razu, oznaczony jako szacunek do weryfikacji
        mvc.perform(get("/api/innovations").param("status", "WYKRYTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].score", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$[*].scoreEstimated", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
        mvc.perform(get("/api/innovations").param("status", "OCENIONA"))
                .andExpect(jsonPath("$[*].scoreEstimated", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
    }

    @Test
    void requestsWithoutCsrfTokenAreRejected() throws Exception {
        MockHttpSession session = login(ANALYST, "demo123");
        mvc.perform(post("/api/radar/scan").session(session)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/radar/scan").session(session).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ANALYST + "\",\"password\":\"zle-haslo\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analystApprovesAssessment() throws Exception {
        Long id = firstWithStatus(InnovationStatus.W_OCENIE);
        String body = "{\"scores\":[]}";
        mvc.perform(post("/api/innovations/" + id + "/assessment/approve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        MockHttpSession analyst = login(ANALYST, "demo123");
        mvc.perform(post("/api/innovations/" + id + "/assessment/approve").session(analyst).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.status.value").value("OCENIONA"));
    }

    @Test
    void spoofedUserHeaderIsIgnored() throws Exception {
        mvc.perform(get("/api/auth/me").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").doesNotExist());
    }
}
