package com.fourp.smartChat;

import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FaqSeeder {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAIClient openAIClient;
    private final String weaviateUrl = "http://localhost:8080";

    public FaqSeeder(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    public void seed() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("faq.json");
        JsonNode root = mapper.readTree(inputStream);

        for (JsonNode node : root) {
            String question = node.get("question").asText();
            String answer = node.get("answer").asText();
            double[] vector = openAIClient.getEmbedding(question);

            uploadToWeaviate(question, answer, vector);
        }
    }

    private void uploadToWeaviate(String question, String answer, double[] vector) throws Exception {
        String compoundKey = question + "::" + answer;
        UUID stableId = UUID.nameUUIDFromBytes(compoundKey.getBytes(StandardCharsets.UTF_8));

        StringBuilder vectorArray = new StringBuilder();
        for (double v : vector) {
            if (vectorArray.length() > 0) vectorArray.append(",");
            vectorArray.append(v);
        }

        String body = "{"
            + "\"id\": \"" + stableId + "\","
            + "\"class\": \"FAQ\","
            + "\"properties\": {\"question\": \"" + question + "\", \"answer\": \"" + answer + "\"},"
            + "\"vector\": [" + vectorArray + "]"
            + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(weaviateUrl + "/v1/objects/" + stableId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("📥 Weaviate response (" + stableId + "): " + response.statusCode());
        System.out.println("📄 Body: " + response.body());
    }
}