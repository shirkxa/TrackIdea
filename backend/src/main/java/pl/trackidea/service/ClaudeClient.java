package pl.trackidea.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dostęp do modelu Claude (structured output). Aktywny tylko, gdy ustawiono ANTHROPIC_API_KEY
 * i nie wyłączono AI w konfiguracji – bez tego cała aplikacja działa na regułach offline.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private final String model;
    private final AnthropicClient client;

    public ClaudeClient(@Value("${trackidea.ai.model:claude-opus-5-5}") String model,
                        @Value("${trackidea.ai.enabled:true}") boolean enabled) {
        this.model = model;
        boolean hasKey = notBlank(System.getenv("ANTHROPIC_API_KEY")) || notBlank(System.getenv("ANTHROPIC_AUTH_TOKEN"));
        this.client = enabled && hasKey ? AnthropicOkHttpClient.fromEnv() : null;
        log.info("Model AI: {}", client != null ? "Claude (" + model + ")" : "wyłączony – reguły offline");
    }

    public boolean available() {
        return client != null;
    }

    public String model() {
        return model;
    }

    /**
     * Jedno zapytanie z odpowiedzią w zadanym schemacie.
     *
     * @throws IllegalStateException gdy model jest wyłączony, odmówił albo nic nie zwrócił
     */
    public <T> T extract(Class<T> schema, String system, String userText) {
        if (client == null) {
            throw new IllegalStateException("model AI jest wyłączony");
        }
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(schema)
                // przy odmowie modelu serwer sam ponawia zapytanie na modelu zapasowym
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(userText)
                .build();
        StructuredMessage<T> response = client.messages().create(params);
        if (response.stopReason().map(r -> r.equals(StopReason.REFUSAL)).orElse(false)) {
            throw new IllegalStateException("model odmówił odpowiedzi");
        }
        return response.content().stream()
                .flatMap(cb -> cb.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("pusta odpowiedź modelu"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
