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

    public String getChatCompletion(String userQuestion, List<String> answers) throws Exception {
        StringBuilder context = new StringBuilder();
        for (String ans : answers) {
            context.append("- ").append(ans).append("\n");
        }

        String formattedPrompt = String.format(faqResponsePrompt, userQuestion, context);
        String filledTemplate = requestTemplate.replace("{{PROMPT}}", mapper.writeValueAsString(formattedPrompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + openAiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(filledTemplate))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body()).at("/choices/0/message/content").asText();
    }
}
