package com.fourp.smartChat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenAIClient {
    private final HttpClient client;
    private final ObjectMapper mapper;
    
    @Value("${openai.api.key}")
    private String openAiKey;
    

    public OpenAIClient() {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
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
            JsonNode json = mapper.readTree(response.body());
            JsonNode vectorNode = json.get("data").get(0).get("embedding");

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

        String prompt = String.format(
            "A potential customer asked: \"%s\"\n\n" +
            "Here are relevant FAQ answers:\n%s\n\n" +
            "Use only the information in these answers to craft a direct, friendly, and relevant reply to the customer. Do not make up new information. Respond clearly and concisely.",
            userQuestion, context.toString()
        );

        String safePrompt = mapper.writeValueAsString(prompt);

        String requestBody =
            "{\n" +
            "  \"model\": \"gpt-3.5-turbo\",\n" +
            "  \"messages\": [\n" +
            "    {\n" +
            "      \"role\": \"system\",\n" +
            "      \"content\": \"You are a helpful assistant for a home services business answering customer questions based on FAQs.\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"role\": \"user\",\n" +
            "      \"content\": " + safePrompt + "\n" +
            "    }\n" +
            "  ],\n" +
            "  \"temperature\": 0.7\n" +
            "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + openAiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());
        return root.at("/choices/0/message/content").asText();
    }
}