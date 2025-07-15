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
    
    public List<String> getFeaturesByTag(String tag, String clientId) {
        try {
            String graphqlQuery = "{"
                    + "\"query\": \"{ Get { Feature(where: {"
                    + "  path: [\\\"tags\\\"],"
                    + "  operator: Equal,"
                    + "  valueString: \\\"" + tag + "\\\""
                    + "},"
                    + " limit: 5) { name description client_id tags } } }\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEAVIATE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode features = root.at("/data/Get/Feature");

            List<String> results = new ArrayList<>();
            if (features.isArray()) {
                for (JsonNode node : features) {
                    String featureClientId = node.get("client_id").asText();
                    if (clientId.equals(featureClientId)) {
                        String name = node.get("name").asText();
                        String description = node.get("description").asText();
                        results.add(name + ": " + description);
                    }
                }
            }

            return results.isEmpty() ? List.of("No matching features found.") : results;

        } catch (Exception e) {
            throw new RuntimeException("Failed to query Weaviate for features by tag", e);
        }
    }

    public List<String> querySimilarEstimates(double[] vector) {
        try {
            StringBuilder vectorArray = new StringBuilder();
            for (double v : vector) {
                if (vectorArray.length() > 0) vectorArray.append(",");
                vectorArray.append(v);
            }

            String body = "{"
                + "\"query\": \"{ Get { Estimate(nearVector: {vector: [" + vectorArray + "]}, limit: 3) { "
                + "category item description cost_min cost_max unit timeframe factors } } }\""
                + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEAVIATE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());
            JsonNode estimates = json.at("/data/Get/Estimate");

            List<String> results = new ArrayList<>();
            if (estimates.isArray()) {
                for (JsonNode node : estimates) {
                    String category = node.get("category").asText();
                    String item = node.get("item").asText();
                    String description = node.get("description").asText();
                    int costMin = node.get("cost_min").asInt();
                    int costMax = node.get("cost_max").asInt();
                    String unit = node.get("unit").asText();
                    String timeframe = node.get("timeframe").asText();

                    String estimateInfo = String.format(
                        "**%s**\n%s\n\n💰 **Cost Range:** $%,d - $%,d %s\n⏱️ **Timeframe:** %s",
                        item, description, costMin, costMax, unit, timeframe
                    );

                    results.add(estimateInfo);
                }
            }

            return results.isEmpty() ? List.of("No matching estimates found.") : results;
        } catch (Exception e) {
            throw new RuntimeException("Weaviate estimate query failed", e);
        }
    }


}