package pl.trackidea.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.trackidea.web.CurrentUser;

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
                        Użytkownika demo wskazuje nagłówek X-User-Id (w wersji produkcyjnej: logowanie / SSO).""")
                .license(new License().name("Prototyp HackNation – dane demo są fikcyjne")));
    }

    /** Dodaje do każdej operacji opcjonalny nagłówek X-User-Id, żeby dało się testować w Swagger UI. */
    @Bean
    public OperationCustomizer userHeader() {
        return (operation, handlerMethod) -> {
            boolean declared = operation.getParameters() != null && operation.getParameters().stream()
                    .anyMatch(p -> CurrentUser.HEADER.equalsIgnoreCase(p.getName()));
            if (!declared) {
                operation.addParametersItem(new HeaderParameter()
                        .name(CurrentUser.HEADER)
                        .required(false)
                        .description("ID użytkownika demo (lista: GET /api/people)")
                        .schema(new IntegerSchema()));
            }
            return operation;
        };
    }
}
