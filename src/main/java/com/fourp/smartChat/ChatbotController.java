package com.fourp.smartChat;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatbotController {

    private final OpenAIClient openAIClient;
    private final WeaviateRestClient weaviateClient;
    private final String CLIENT_ID = "peachstate";

    public ChatbotController(OpenAIClient openAIClient, WeaviateRestClient weaviateClient) {
        this.openAIClient = openAIClient;
        this.weaviateClient = weaviateClient;
    }

    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody Map<String, String> request) throws Exception{
        String question = request.get("question");
        double[] embedding = openAIClient.getEmbedding(question);
        List<String> answers = weaviateClient.querySimilarAnswers(embedding);
        List<String> allowedTags = List.of(
        	    "trust", "compliance", "technology", "reputation",
        	    "speed", "urgency", "affordability", "community"
        	);

        String intentTag = openAIClient.detectIntentTag(question, allowedTags);
        List<String> features = new ArrayList<String>();
        if(intentTag != null) {
        	features = weaviateClient.getFeaturesByTag(intentTag, CLIENT_ID);
        }
        
        
        String friendlyAnswer = openAIClient.getChatCompletion(question, answers, features);
        return Map.of("question", question, "answer", friendlyAnswer);
        
    }
}