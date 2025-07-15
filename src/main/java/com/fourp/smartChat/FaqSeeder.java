package com.fourp.smartChat;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
	public void seedFAQs() throws Exception {
	    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("faq.json");
	    JsonNode root = mapper.readTree(inputStream);

	    for (JsonNode node : root) {
	        String externalId = node.get("id").asText(); // human-friendly ID in file
	        String question = node.get("question").asText();
	        String answer = node.get("answer").asText();
	        String clientId = node.get("client_id").asText();

	        UUID stableUuid = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
	        double[] vector = openAIClient.getEmbedding(question);

	        StringBuilder vectorArray = new StringBuilder();
	        for (double v : vector) {
	            if (vectorArray.length() > 0) vectorArray.append(",");
	            vectorArray.append(v);
	        }

	        String jsonBody = "{"
	            + "\"id\": \"" + stableUuid + "\","
	            + "\"class\": \"FAQ\","
	            + "\"properties\": {"
	            + "\"external_id\": \"" + externalId + "\","
	            + "\"question\": \"" + escapeJson(question) + "\","
	            + "\"answer\": \"" + escapeJson(answer) + "\","
	            + "\"client_id\": \"" + clientId + "\""
	            + "},"
	            + "\"vector\": [" + vectorArray + "]"
	            + "}";

	        uploadToWeaviate("FAQ", stableUuid.toString(), jsonBody);
	    }
	}


	public void seedFeatures() throws Exception {
	    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("features.json");
	    JsonNode root = mapper.readTree(inputStream);

	    for (JsonNode node : root) {
	        String externalId = node.has("id") ? node.get("id").asText() : null;
	        String name = node.get("name").asText();
	        String description = node.get("description").asText();
	        String clientId = node.get("client_id").asText();

	        double[] vector = openAIClient.getEmbedding(name);
	        UUID stableId = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));

	        StringBuilder vectorArray = new StringBuilder();
	        for (double v : vector) {
	            if (vectorArray.length() > 0) vectorArray.append(",");
	            vectorArray.append(v);
	        }

	        // Format tags
	        StringBuilder tagArray = new StringBuilder();
	        JsonNode tagsNode = node.get("tags");
	        if (tagsNode != null && tagsNode.isArray()) {
	            for (int i = 0; i < tagsNode.size(); i++) {
	                if (i > 0) tagArray.append(",");
	                tagArray.append("\"").append(tagsNode.get(i).asText()).append("\"");
	            }
	        }

	        String jsonBody = "{"
	                + "\"id\": \"" + stableId + "\","
	                + "\"class\": \"Feature\","
	                + "\"properties\": {"
	                + "\"name\": \"" + escapeJson(name) + "\","
	                + "\"description\": \"" + escapeJson(description) + "\","
	                + "\"tags\": [" + tagArray + "],"
	                + "\"client_id\": \"" + clientId + "\","
	                + "\"external_id\": \"" + externalId + "\""
	                + "},"
	                + "\"vector\": [" + vectorArray + "]"
	                + "}";

	        uploadToWeaviate("Feature", stableId.toString(), jsonBody);
	    }
	}

	public void seedEstimates() throws Exception {
	    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("estimates.json");
	    JsonNode root = mapper.readTree(inputStream);

	    for (JsonNode node : root) {
	        String externalId = node.get("id").asText();
	        String category = node.get("category").asText();
	        String item = node.get("item").asText();
	        String description = node.get("description").asText();
	        int costMin = node.get("cost_min").asInt();
	        int costMax = node.get("cost_max").asInt();
	        String unit = node.get("unit").asText();
	        String timeframe = node.get("timeframe").asText();

	        // Create searchable text for embedding
	        String searchableText = category + " " + item + " " + description;
	        double[] vector = openAIClient.getEmbedding(searchableText);
	        UUID stableId = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));

	        StringBuilder vectorArray = new StringBuilder();
	        for (double v : vector) {
	            if (vectorArray.length() > 0) vectorArray.append(",");
	            vectorArray.append(v);
	        }

	        // Format factors array
	        StringBuilder factorsArray = new StringBuilder();
	        JsonNode factorsNode = node.get("factors");
	        if (factorsNode != null && factorsNode.isArray()) {
	            for (int i = 0; i < factorsNode.size(); i++) {
	                if (i > 0) factorsArray.append(",");
	                factorsArray.append("\"").append(escapeJson(factorsNode.get(i).asText())).append("\"");
	            }
	        }

	        String jsonBody = "{"
	                + "\"id\": \"" + stableId + "\","
	                + "\"class\": \"Estimate\","
	                + "\"properties\": {"
	                + "\"external_id\": \"" + externalId + "\","
	                + "\"category\": \"" + escapeJson(category) + "\","
	                + "\"item\": \"" + escapeJson(item) + "\","
	                + "\"description\": \"" + escapeJson(description) + "\","
	                + "\"cost_min\": " + costMin + ","
	                + "\"cost_max\": " + costMax + ","
	                + "\"unit\": \"" + escapeJson(unit) + "\","
	                + "\"timeframe\": \"" + escapeJson(timeframe) + "\","
	                + "\"factors\": [" + factorsArray + "]"
	                + "},"
	                + "\"vector\": [" + vectorArray + "]"
	                + "}";

	        uploadToWeaviate("Estimate", stableId.toString(), jsonBody);
	    }
	}



	private void uploadToWeaviate(String className, String id, String jsonBody) throws Exception {
		String objectUrl = weaviateUrl + "/v1/objects/" + id;

		HttpRequest getRequest = HttpRequest.newBuilder().uri(URI.create(objectUrl)).GET().build();

		HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

		HttpRequest writeRequest;
		if (getResponse.statusCode() == 200) {
			// Object exists — update with PUT
			writeRequest = HttpRequest.newBuilder().uri(URI.create(objectUrl))
					.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
					.build();
		} else {
			// Object doesn't exist — create with POST
			writeRequest = HttpRequest.newBuilder().uri(URI.create(weaviateUrl + "/v1/objects"))
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody))
					.build();
		}

		HttpResponse<String> response = client.send(writeRequest, HttpResponse.BodyHandlers.ofString());
		System.out.printf("📥 Weaviate %s response (%s): %d%n📄 Body: %s%n", className, id, response.statusCode(),
				response.body());
	}


	private String escapeJson(String value) {
		return value.replace("\"", "\\\"").replace("\n", "\\n");
	}

}
