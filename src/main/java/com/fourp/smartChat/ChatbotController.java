package com.fourp.smartChat;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatbotController {

    private final OpenAIClient openAIClient;
    private final WeaviateRestClient weaviateClient;
    private final String CLIENT_ID = "peachstate";
    private final int MAX_RESPONSE_LENGTH = 800; // Character limit for responses

    public ChatbotController(OpenAIClient openAIClient, WeaviateRestClient weaviateClient) {
        this.openAIClient = openAIClient;
        this.weaviateClient = weaviateClient;
    }

    private String limitResponseLength(String response) {
        if (response.length() <= MAX_RESPONSE_LENGTH) {
            return response;
        }
        
        // Find the last complete sentence within the limit
        String truncated = response.substring(0, MAX_RESPONSE_LENGTH);
        int lastPeriod = truncated.lastIndexOf('.');
        int lastQuestion = truncated.lastIndexOf('?');
        int lastExclamation = truncated.lastIndexOf('!');
        
        int lastSentenceEnd = Math.max(lastPeriod, Math.max(lastQuestion, lastExclamation));
        
        if (lastSentenceEnd > MAX_RESPONSE_LENGTH * 0.6) { // Only truncate if we don't lose too much
            return truncated.substring(0, lastSentenceEnd + 1);
        }
        
        return truncated + "...";
    }
    
    private String detectSchedulingIntent(String question) {
        String lowerQuestion = question.toLowerCase().trim();
        
        // Positive responses
        String[] positiveKeywords = {
            "yes", "yeah", "yep", "sure", "ok", "okay", "alright", "sounds good",
            "let's do it", "please", "i would", "i'd like", "that works", "perfect",
            "go ahead", "proceed", "schedule", "book", "set up", "arrange"
        };
        
        // Negative responses
        String[] negativeKeywords = {
            "no", "nah", "nope", "not now", "not interested", "maybe later", "skip",
            "don't want", "won't", "can't", "not really", "i'm good", "pass", "decline"
        };
        
        // Check for positive intent
        for (String keyword : positiveKeywords) {
            if (lowerQuestion.contains(keyword)) {
                return "positive";
            }
        }
        
        // Check for negative intent
        for (String keyword : negativeKeywords) {
            if (lowerQuestion.contains(keyword)) {
                return "negative";
            }
        }
        
        // If unclear, return null to continue normal processing
        return null;
    }
    
    private boolean isGenericResponse(String response) {
        String[] genericKeywords = {
            "wiring", "electrical work", "outlets", "lighting", "panel", "switches",
            "electrical issues", "electrical problems", "electrical repairs", "rewiring",
            "installation", "upgrade", "fix", "repair", "replace", "install"
        };
        
        String lower = response.toLowerCase();
        int wordCount = lower.split("\\s+").length;
        
        // Consider it generic if it's short (≤6 words) and contains generic keywords
        if (wordCount <= 6) {
            for (String keyword : genericKeywords) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private String generateIntelligentFollowUp(String response) throws Exception {
        String prompt = "The user said: '" + response + "'\n\n" +
            "This is too generic for an electrical estimate. Generate 1-2 specific follow-up questions to understand:\n" +
            "1. What type of electrical work exactly\n" +
            "2. Location/scope of work (which rooms, whole house, etc.)\n\n" +
            "Keep it conversational, helpful, and very concise (under 100 words). " +
            "Format as a friendly response with bullet points for clarity.";
        
        return openAIClient.getChatCompletion(response, List.of(), List.of(), prompt);
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, Object> request) throws Exception{
        String question = (String) request.get("question");
        String conversationId = (String) request.get("conversationId");
        String conversationState = (String) request.get("conversationState");
        
        // Check for button selections
        if (question.equals("EMERGENCY_BUTTON")) {
            return handleEmergencySelection(conversationId);
        } else if (question.equals("ESTIMATE_BUTTON")) {
            return handleEstimateSelection(conversationId);
        } else if (question.equals("SCHEDULE_BUTTON")) {
            return handleScheduleSelection(conversationId);
        } else if (question.equals("QUESTION_BUTTON")) {
            return handleQuestionSelection(conversationId);
        } else if (question.equals("TECHNICIAN_BUTTON")) {
            return handleTechnicianSelection(conversationId);
        }
        
        // Handle estimate flow
        if ("collecting_estimate_details".equals(conversationState)) {
            return handleEstimateQuery(question, conversationId);
        }
        
        // Handle final estimate details collection (no more follow-ups)
        if ("collecting_estimate_details_final".equals(conversationState)) {
            return handleFinalEstimateQuery(question, conversationId);
        }
        
        // Check for schedule confirmation
        if (conversationState != null && conversationState.contains("schedule_call")) {
            String intent = detectSchedulingIntent(question);
            if ("positive".equals(intent)) {
                return handleScheduleCallConfirmation(conversationId);
            } else if ("negative".equals(intent)) {
                return handleScheduleCallDeclined(conversationId);
            }
        }
        
        // Normal FAQ + features lookup for regular questions
        return handleNormalRequest(question, conversationId);
    }
    
    private Map<String, Object> handleEmergencySelection(String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "emergency");
        
        String emergencyResponse = "🚨 **ELECTRICAL EMERGENCY** \n" +
            "**⚠️ CALL US IMMEDIATELY ⚠️**\n\n" +
            "📞   **(404) 555-1212**\n\n" +
            "**🛡️ SAFETY FIRST:**\n\n" +
            "• 🔌 Turn off power at the main breaker (if safe)\n" +
            "• ⚡ Stay away from sparking outlets or wires\n" +
            "• 🔥 Evacuate if you smell burning or see smoke\n" +
            "• 🚫 Don't touch electrical panels if wet\n\n\n" +
            "**⏰ EMERGENCY RESPONSE:**\n\n" +
            "• Available 24/7 - 365 days a year\n" +
            "• Licensed emergency electricians\n" +
            "• Response time: Within 60 minutes\n" +
            "• Serving all Atlanta metro areas\n\n\n" ;
  
            
        response.put("answer", emergencyResponse);
        return response;
    }
    
    private Map<String, Object> handleEstimateSelection(String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "estimate");
        
        String estimateResponse = "I'd be happy to help you get a free estimate!\n\n" +
            "We do most residential electrical work. What type of work do you need?";
            
        response.put("answer", limitResponseLength(estimateResponse));
        response.put("conversationState", "collecting_estimate_details");
        return response;
    }
    
    private Map<String, Object> handleEstimateQuery(String question, String conversationId) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "estimate_query");
        
        // Check if response is too generic and needs clarification (limit to 1 follow-up)
        if (isGenericResponse(question)) {
            String followUpQuestions = generateIntelligentFollowUp(question);
            response.put("answer", limitResponseLength(followUpQuestions));
            response.put("conversationState", "collecting_estimate_details_final"); // One follow-up only
            return response;
        }
        
        // Query Weaviate for similar estimates
        double[] embedding = openAIClient.getEmbedding(question);
        List<String> estimates = weaviateClient.querySimilarEstimates(embedding);
        
        String estimateResponse;
        if (estimates.size() > 0 && !estimates.get(0).equals("No matching estimates found.")) {
            estimateResponse = "Here are estimated price ranges:\n\n";
            // Show only the first estimate to keep it concise
            estimateResponse += estimates.get(0) + "\n\n";
            estimateResponse += "**⚠️ Prices vary based on your specific situation**\n\n" +
                "**Schedule a call for a personalized quote?**";
        } else {
            estimateResponse = "I don't have specific pricing for that work.\n\n" +
                "Our electricians can provide estimates for custom projects, specialized installations, and code upgrades.\n\n" +
                "**Schedule a call for a personalized quote?**";
        }
        
        response.put("answer", limitResponseLength(estimateResponse));
        response.put("conversationState", "awaiting_schedule_call_confirmation");
        return response;
    }
    
    private Map<String, Object> handleFinalEstimateQuery(String question, String conversationId) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "final_estimate_query");
        
        // Query Weaviate for similar estimates (no more follow-ups)
        double[] embedding = openAIClient.getEmbedding(question);
        List<String> estimates = weaviateClient.querySimilarEstimates(embedding);
        
        String estimateResponse;
        if (estimates.size() > 0 && !estimates.get(0).equals("No matching estimates found.")) {
            estimateResponse = "Based on your details, here are estimated price ranges:\n\n";
            // Show only the first estimate to keep it concise
            estimateResponse += estimates.get(0) + "\n\n";
            estimateResponse += "**⚠️ Prices vary based on your specific situation**\n\n" +
                "**Schedule a call for a personalized quote?**";
        } else {
            estimateResponse = "I don't have specific pricing for that type of work.\n\n" +
                "Our electricians can provide estimates for custom projects, specialized installations, and code upgrades.\n\n" +
                "**Would you like to schedule a call or contact us at (404) 555-1212?**";
        }
        
        response.put("answer", limitResponseLength(estimateResponse));
        response.put("conversationState", "awaiting_schedule_call_confirmation");
        return response;
    }
    
    private Map<String, Object> handleScheduleCallConfirmation(String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "schedule_call");
        
        String scheduleResponse = "Perfect! Let's schedule your free estimate call.\n\n" +
            "**📞 What to expect:**\n" +
            "• 15-30 minute consultation\n" +
            "• Project assessment & accurate quote\n\n" +
            "Please select your preferred time:";
            
        response.put("answer", limitResponseLength(scheduleResponse));
        response.put("nextStep", "show_calendly");
        return response;
    }
    
    private Map<String, Object> handleScheduleCallDeclined(String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "schedule_declined");
        
        String declineResponse = "No problem! Other ways to get help:\n\n" +
            "**📞 Call:** (404) 555-1212 (Mon-Fri 8AM-6PM)\n" +
            "**📧 Email:** estimates@peachstateelectric.com\n" +
            "**💬 Continue chatting:** Ask me more questions!\n\n" +
            "**What else can I help you with?**";
            
        response.put("answer", limitResponseLength(declineResponse));
        response.put("conversationState", null); // Clear conversation state
        return response;
    }
    
    private Map<String, Object> handleScheduleSelection(String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "schedule");
        
        String scheduleResponse = "Perfect! Let's get you scheduled for service.\n\n" +
            "I'll show you our available appointment times. You can select a time that works best for you, and we'll send you a confirmation with all the details.\n\n" +
            "Our service hours are:\n" +
            "• Monday-Friday: 8 AM - 6 PM\n" +
            "• Saturday: 8 AM - 4 PM\n" +
            "• Emergency service: 24/7\n\n" +
            "Please select your preferred appointment time below:";
            
        response.put("answer", scheduleResponse);
        response.put("nextStep", "show_calendly");
        return response;
    }
    
    private Map<String, Object> handleQuestionSelection(String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "question");
        
        String questionResponse = "I'm here to answer your electrical questions!\n\n" +
            "You can ask me about:\n" +
            "• Our services and pricing\n" +
            "• Electrical safety tips\n" +
            "• Code requirements\n" +
            "• Service areas\n" +
            "• Licensing and insurance\n\n" +
            "What would you like to know?";
            
        response.put("answer", questionResponse);
        return response;
    }
    
    private Map<String, Object> handleTechnicianSelection(String conversationId) {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("actionType", "technician");
        
        String technicianResponse = "I'll connect you with one of our licensed electricians!\n\n" +
            "**Please call us at (404) 555-1212** to speak directly with a technician.\n\n" +
            "Our technicians are available:\n" +
            "• Monday-Friday: 8 AM - 6 PM\n" +
            "• Saturday: 8 AM - 4 PM\n" +
            "• Emergency service: 24/7\n\n" +
            "They can help with technical questions, troubleshooting, and detailed project planning.\n\n" +
            "**Call now: (404) 555-1212**";
            
        response.put("answer", technicianResponse);
        return response;
    }
    
    private Map<String, Object> handleNormalRequest(String question, String conversationId) throws Exception {
        // Improved FAQ + features lookup with better context
        double[] embedding = openAIClient.getEmbedding(question);
        List<String> answers = weaviateClient.querySimilarAnswers(embedding);
        
        // Enhanced intent detection with more nuanced tags
        List<String> allowedTags = List.of(
            "trust", "compliance", "technology", "reputation",
            "speed", "urgency", "affordability", "community"
        );
        
        String intentTag = openAIClient.detectIntentTag(question, allowedTags);
        List<String> features = new ArrayList<>();
        
        if (intentTag != null) {
            features = weaviateClient.getFeaturesByTag(intentTag, CLIENT_ID);
        }
        
        // More context-aware prompt for better responses
        String enhancedPrompt = buildEnhancedPrompt(question, answers, features, intentTag);
        String friendlyAnswer = openAIClient.getChatCompletion(question, answers, features, enhancedPrompt);
        
        Map<String, Object> response = new HashMap<>();
        response.put("question", question);
        response.put("answer", friendlyAnswer);
        response.put("conversationId", conversationId);
        response.put("intentTag", intentTag);
        response.put("isEmergency", false);
        response.put("isScheduling", false);
        
        return response;
    }
    
    private String buildEnhancedPrompt(String question, List<String> answers, List<String> features, String intentTag) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful customer service representative for Peach State Electric, a trusted electrical contractor in Atlanta.\n\n");
        
        if (intentTag != null) {
            prompt.append("The customer seems interested in: ").append(intentTag).append("\n\n");
        }
        
        prompt.append("Customer question: \"").append(question).append("\"\n\n");
        
        if (!answers.isEmpty()) {
            prompt.append("Relevant FAQ information:\n");
            for (int i = 0; i < answers.size(); i++) {
                prompt.append("• ").append(answers.get(i)).append("\n");
            }
            prompt.append("\n");
        }
        
        if (!features.isEmpty()) {
            prompt.append("Relevant company features to highlight:\n");
            for (String feature : features) {
                prompt.append("• ").append(feature).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("Instructions:\n");
        prompt.append("1. Provide a helpful, conversational response\n");
        prompt.append("2. Use the FAQ information as your primary source\n");
        prompt.append("3. Naturally incorporate relevant company features\n");
        prompt.append("4. If scheduling is mentioned, guide them to provide more details\n");
        prompt.append("5. For emergencies, emphasize calling (404) 555-1212 immediately\n");
        prompt.append("6. Be warm, professional, and locally focused on Atlanta\n");
        prompt.append("7. End with a follow-up question or offer to help further\n");
        
        return prompt.toString();
    }
}