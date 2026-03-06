package edu.instructor.tester.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import edu.instructor.tester.model.CheckResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Falls back to Claude Sonnet for evaluating getMean/getMedian correctness
 * when the student program doesn't print individual values to stdout.
 */
@Service
public class AiAnalysisService {

    private final AnthropicClient client;

    public AiAnalysisService() {
        AnthropicClient c = null;
        try {
            c = AnthropicOkHttpClient.fromEnv();
        } catch (Exception ignored) {
            // API key not set — client stays null; analyzeMethod will return WARN
        }
        this.client = c;
    }

    /**
     * Sends the extracted method code to Claude Sonnet and returns a CheckResult
     * indicating whether the method correctly computes mean or median.
     */
    public List<CheckResult> analyzeMethod(String methodCode, String methodName) {
        if (client == null) {
            return List.of(CheckResult.warn(CheckResult.Category.AI_ANALYSIS,
                    methodName + "() correctness (AI)",
                    "AI analysis unavailable — ANTHROPIC_API_KEY is not set."));
        }

        String prompt = """
                You are evaluating a student's Java method for correctness.
                The method is named "%s" and should compute the %s of an ArrayList<Integer>.

                Here is the method:
                ```java
                %s
                ```

                Evaluate whether this method correctly computes the %s.
                For getMean: it should sum all elements and divide by the count using floating-point division.
                For getMedian: it should sort (or expect a sorted list), then return the middle element \
                (or average of the two middle elements for even-length lists).

                Respond with EXACTLY "PASS" or "FAIL" on the first line.
                On subsequent lines, provide a brief explanation (2-3 sentences max).
                """.formatted(methodName, descriptionFor(methodName), methodCode, descriptionFor(methodName));

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.of("claude-sonnet-4-6"))
                    .maxTokens(512L)
                    .addUserMessage(prompt)
                    .build();

            Message message = client.messages().create(params);

            // Extract text from content blocks
            String responseText = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(tb -> tb.text())
                    .reduce("", String::concat);

            return List.of(parseAiResponse(responseText, methodName));

        } catch (Exception e) {
            return List.of(CheckResult.warn(CheckResult.Category.AI_ANALYSIS,
                    methodName + "() correctness (AI)",
                    "AI analysis unavailable — " + e.getMessage()));
        }
    }

    private CheckResult parseAiResponse(String response, String methodName) {
        String checkName = methodName + "() correctness (AI)";
        if (response == null || response.isBlank()) {
            return CheckResult.warn(CheckResult.Category.AI_ANALYSIS, checkName,
                    "AI returned an empty response.");
        }

        String[] lines = response.strip().split("\\r?\\n", 2);
        String verdict = lines[0].trim().toUpperCase();
        String explanation = lines.length > 1 ? lines[1].trim() : "No explanation provided.";

        return switch (verdict) {
            case "PASS" -> CheckResult.pass(CheckResult.Category.AI_ANALYSIS, checkName, explanation);
            case "FAIL" -> CheckResult.fail(CheckResult.Category.AI_ANALYSIS, checkName, explanation);
            default     -> CheckResult.warn(CheckResult.Category.AI_ANALYSIS, checkName,
                    "AI returned an unexpected verdict: " + response.strip());
        };
    }

    private String descriptionFor(String methodName) {
        return switch (methodName) {
            case "getMean"   -> "arithmetic mean (average)";
            case "getMedian" -> "median (middle value of sorted data)";
            default          -> methodName;
        };
    }
}
