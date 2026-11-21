package pl.trackidea.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.trackidea.repo.PersonRepository;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profil produkcyjny: bez logowania jednym kliknięciem, bez znanego hasła demo, z bazą na dysku. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionProfileTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) {
        registry.add("TRACKIDEA_DATA_DIR", () -> data.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    PersonRepository people;

    @Test
    void demoLoginIsOffAndDemoPasswordDoesNotWork() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.demoMode").value(false));
        Long admin = people.findByEmailIgnoreCase("administrator.pci@pci.example.org").orElseThrow().getId();
        mvc.perform(post("/api/auth/demo/" + admin).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"k.nowak@pci.example.org\",\"password\":\"demo123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseLivesOnDiskSoAnalystWorkSurvivesRestart() {
        assertThat(Files.exists(data.resolve("trackidea.mv.db"))).isTrue();
    }
}
