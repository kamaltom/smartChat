package com.fourp.smartChat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeaviateRestClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String WEAVIATE_URL = "http://localhost:8080/v1/graphql";

    public List<String> querySimilarAnswers(double[] vector) {
        try {
            StringBuilder vectorArray = new StringBuilder();
            for (double v : vector) {
                if (vectorArray.length() > 0) vectorArray.append(",");
                vectorArray.append(v);
            }

            String body = "{"
                + "\"query\": \"{ Get { FAQ(nearVector: {vector: [" + vectorArray + "]}, limit: 3) { question answer } } }\""
                + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEAVIATE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());
            JsonNode faqs = json.at("/data/Get/FAQ");

            List<String> answers = new ArrayList<String>();
            if (faqs.isArray()) {
                for (JsonNode node : faqs) {
                    JsonNode ansNode = node.get("answer");
                    if (ansNode != null && !ansNode.asText().isBlank()) {
                        answers.add(ansNode.asText());
                    }
                }
            }

            return answers.isEmpty() ? List.of("No good match found.") : answers;
        } catch (Exception e) {
            throw new RuntimeException("Weaviate query failed", e);
        }
    }

}