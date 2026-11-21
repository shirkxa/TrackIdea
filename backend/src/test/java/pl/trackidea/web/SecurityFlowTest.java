package pl.trackidea.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.repo.InnovationRepository;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Logowanie, CSRF i uprawnienia ról – na pełnym kontekście aplikacji z danymi demo. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFlowTest {

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

    @Test
    void catalogIsPublicButChangesRequireLogin() throws Exception {
        mvc.perform(get("/api/innovations")).andExpect(status().isOk());
        mvc.perform(post("/api/innovations/1/vote").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutCsrfTokenAreRejected() throws Exception {
        MockHttpSession session = login("anna.kowalczyk@example.org", "demo123");
        mvc.perform(post("/api/innovations/1/vote").session(session)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/innovations/1/vote").session(session).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna.kowalczyk@example.org\",\"password\":\"zle-haslo\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyPciExpertCanApproveAssessment() throws Exception {
        List<Innovation> inReview = innovations.findByStatusIn(List.of(InnovationStatus.W_OCENIE));
        Long id = inReview.getFirst().getId();
        String body = "{\"scores\":[]}";

        MockHttpSession creator = login("anna.kowalczyk@example.org", "demo123");
        mvc.perform(post("/api/innovations/" + id + "/assessment/approve").session(creator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        MockHttpSession expert = login("k.nowak@pci.example.org", "demo123");
        mvc.perform(post("/api/innovations/" + id + "/assessment/approve").session(expert).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.status.value").value("OCENIONA"));
    }

    @Test
    void spoofedUserHeaderIsIgnored() throws Exception {
        mvc.perform(get("/api/auth/me").header("X-User-Id", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").doesNotExist());
    }
}
