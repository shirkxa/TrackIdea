package pl.trackidea.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Dokumentacja REST API dla integracji z innymi systemami: /swagger-ui.html, specyfikacja: /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trackIdeaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TrackIdea API")
                .version("0.1.0")
                .description("""
                        REST API platformy TrackIdea – radar ukrytych innowacji z instytucji publicznych.
                        Pozwala innym systemom (np. PCI, uczelni, samorządu) pobierać katalog i karty innowacji,
                        zgłaszać innowacje, dopasowania i mierniki oraz eksportować dane do CSV.
                        Zapis wymaga zalogowania (POST /api/auth/login, sesja + ochrona CSRF); odczyt katalogu jest publiczny.""")
                .license(new License().name("Prototyp HackNation – dane demo są fikcyjne")));
    }
}
