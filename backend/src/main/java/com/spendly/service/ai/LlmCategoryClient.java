package com.spendly.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls a chat-completions style LLM API (Groq by default) to classify an
 * expense description into one of the user's category names.
 *
 * The model is constrained to answer with exactly one of the provided names;
 * the response is still validated upstream against the user's real categories.
 */
@Component
public class LlmCategoryClient implements AiCategoryClient {

    private static final Logger log = LoggerFactory.getLogger(LlmCategoryClient.class);

    /**
     * Generous because the default model is a reasoning model: its thinking
     * tokens come out of the same budget as the answer, and a budget sized for
     * a one-word reply gets spent before the reply is produced. The answer is
     * still one category name; this only stops it being truncated to nothing.
     */
    private static final int MAX_TOKENS = 512;

    private final boolean enabled;
    private final String model;
    private final String reasoningEffort;
    private final RestClient restClient;

    public LlmCategoryClient(
            @Value("${spendly.ai.enabled:true}") boolean enabled,
            @Value("${spendly.ai.api-key:}") String apiKey,
            @Value("${spendly.ai.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${spendly.ai.model:openai/gpt-oss-20b}") String model,
            @Value("${spendly.ai.reasoning-effort:low}") String reasoningEffort,
            @Value("${spendly.ai.timeout-ms:5000}") int timeoutMs
    ) {
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        this.enabled = enabled && hasKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;

        // Falling back to the heuristic is a silent, successful-looking response,
        // so say once at startup which mode we are actually in.
        if (this.enabled) {
            log.info("AI category suggestions enabled (model={}, baseUrl={})", model, baseUrl);
        } else if (!hasKey) {
            log.warn("AI category suggestions disabled: spendly.ai.api-key is not set. "
                    + "Suggestions will use the keyword heuristic.");
        } else {
            log.info("AI category suggestions disabled by configuration.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Optional<String> pickCategory(String description, List<String> categoryNames) {
        if (!enabled || categoryNames.isEmpty()) {
            return Optional.empty();
        }
        try {
            String systemPrompt = "You classify personal expenses. Reply with exactly one of the following "
                    + "category names and nothing else: " + String.join(", ", categoryNames)
                    + ". If none fits well, reply with the closest one.";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0);
            body.put("max_tokens", MAX_TOKENS);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", description)));
            // Only sent when configured: it is meaningful to reasoning models and
            // rejected outright by some other OpenAI-compatible providers, and the
            // base URL is deliberately swappable.
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                body.put("reasoning_effort", reasoningEffort);
            }

            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.warn("AI category suggestion returned no choices, falling back to heuristic");
                return Optional.empty();
            }
            Choice choice = response.choices().get(0);
            String content = choice.message().content();
            if (content == null || content.isBlank()) {
                // The usual cause is a reasoning model spending the whole token
                // budget before it writes an answer.
                log.warn("AI category suggestion came back empty (finish_reason={}), falling back to heuristic",
                        choice.finishReason());
                return Optional.empty();
            }
            return Optional.of(content.trim());
        } catch (RestClientResponseException e) {
            log.warn("AI category suggestion rejected by provider ({}), falling back to heuristic: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI category suggestion failed, falling back to heuristic: {}", e.toString());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }
}
