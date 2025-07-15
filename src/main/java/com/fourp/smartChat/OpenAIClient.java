package com.fourp.smartChat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class OpenAIClient {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openai.api.key}")
    private String openAiKey;

    @Value("${openai.prompt.faqResponse}")
    private String faqResponsePrompt;

    @Value("${openai.prompt.templatePath}")
    private String promptTemplatePath;

    private String requestTemplate;

    @PostConstruct
    private void loadPromptTemplate() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(promptTemplatePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Prompt template not found: " + promptTemplatePath);
        }
        requestTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    public double[] getEmbedding(String input) {
        try {
            String body = String.format("{\"input\": \"%s\", \"model\": \"text-embedding-3-small\"}", input);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/embeddings"))
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode vectorNode = mapper.readTree(response.body()).get("data").get(0).get("embedding");

            double[] vector = new double[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = vectorNode.get(i).asDouble();
            }
            return vector;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI embedding failed", e);
        }
    }

    public String getChatCompletion(String userQuestion, List<String> answers, List<String> features) throws Exception {
        // Build context string
        StringBuilder context = new StringBuilder();
        for (String ans : answers) {
            context.append("- ").append(ans).append("\n");
        }

        if (!features.isEmpty()) {
            context.append("\nFeatures:\n");
            for (String feature : features) {
                context.append("- ").append(feature).append("\n");
            }
        }

        String finalPrompt = String.format(faqResponsePrompt, userQuestion, context.toString());

        // Replace {{PROMPT}} with actual prompt (unescaped)
        String filledTemplate = requestTemplate.replace("\"{{PROMPT}}\"", mapper.writeValueAsString(finalPrompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + openAiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(filledTemplate))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.body());
        }

        return mapper.readTree(response.body()).at("/choices/0/message/content").asText();
    }

    // Enhanced method with custom prompt
    public String getChatCompletion(String userQuestion, List<String> answers, List<String> features, String customPrompt) throws Exception {
        // Replace {{PROMPT}} with custom prompt (unescaped)
        String filledTemplate = requestTemplate.replace("\"{{PROMPT}}\"", mapper.writeValueAsString(customPrompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + openAiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(filledTemplate))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API error: " + response.body());
        }

        return mapper.readTree(response.body()).at("/choices/0/message/content").asText();
    }


    
    public String detectIntentTag(String userQuestion, List<String> allowedTags) throws Exception {
        String systemMessage = "You are a classifier. Your job is to map user questions to one intent tag from the following list:\n" +
                String.join(", ", allowedTags) + "\n\n" +
                "Pick the tag that best describes the underlying customer concern or priority. Only respond with one of the allowed tags exactly.";

        String requestJson = "{\n" +
                "  \"model\": \"gpt-3.5-turbo\",\n" +
                "  \"messages\": [\n" +
                "    { \"role\": \"system\", \"content\": " + mapper.writeValueAsString(systemMessage) + " },\n" +
                "    { \"role\": \"user\", \"content\": " + mapper.writeValueAsString(userQuestion) + " }\n" +
                "  ],\n" +
                "  \"temperature\": 0.0\n" +
                "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + openAiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String tag = mapper.readTree(response.body()).at("/choices/0/message/content").asText();

        // Return lowercase tag for consistency
        return tag.trim().toLowerCase();
    }

}
