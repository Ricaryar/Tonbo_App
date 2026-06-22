package com.example.tonbo_app;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversation manager
 * Manages conversation context, history, and session state
 */
public class ConversationManager {
    private static final String TAG = "ConversationManager";
    private static final int MAX_HISTORY_SIZE = 20; // Maximum 20 conversation turns
    
    // Conversation history
    private List<ConversationTurn> conversationHistory;
    
    // Current session state
    private ConversationState currentState;
    
    // User information (extensible)
    private String userName;
    
    public enum ConversationState {
        IDLE,           // Idle state
        LISTENING,      // Listening
        PROCESSING,     // Processing
        RESPONDING      // Responding
    }
    
    /**
     * Conversation turn
     */
    public static class ConversationTurn {
        public String userInput;      // User input
        public String assistantResponse; // Assistant response
        public boolean isCommand;      // Whether it's a command
        public String commandType;    // Command type (if it's a command)
        public long timestamp;        // Timestamp
        
        public ConversationTurn(String userInput, String assistantResponse, boolean isCommand, String commandType) {
            this.userInput = userInput;
            this.assistantResponse = assistantResponse;
            this.isCommand = isCommand;
            this.commandType = commandType;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public ConversationManager() {
        conversationHistory = new ArrayList<>();
        currentState = ConversationState.IDLE;
    }
    
    /**
     * Add conversation turn
     */
    public void addTurn(String userInput, String assistantResponse, boolean isCommand, String commandType) {
        ConversationTurn turn = new ConversationTurn(userInput, assistantResponse, isCommand, commandType);
        conversationHistory.add(turn);
        
        // Limit history size
        if (conversationHistory.size() > MAX_HISTORY_SIZE) {
            conversationHistory.remove(0);
        }
        
        Log.d(TAG, "Add conversation turn: " + userInput + " -> " + assistantResponse);
    }
    
    /**
     * Get recent conversation history
     */
    public List<ConversationTurn> getRecentHistory(int count) {
        int start = Math.max(0, conversationHistory.size() - count);
        return new ArrayList<>(conversationHistory.subList(start, conversationHistory.size()));
    }
    
    /**
     * Get all conversation history
     */
    public List<ConversationTurn> getAllHistory() {
        return new ArrayList<>(conversationHistory);
    }
    
    /**
     * Get context information (for generating responses)
     */
    public String getContextSummary() {
        if (conversationHistory.isEmpty()) {
            return "";
        }
        
        StringBuilder summary = new StringBuilder();
        List<ConversationTurn> recent = getRecentHistory(3); // Recent 3 conversation turns
        
        for (ConversationTurn turn : recent) {
            summary.append("用戶: ").append(turn.userInput).append("\n");
            if (turn.assistantResponse != null) {
                summary.append("助手: ").append(turn.assistantResponse).append("\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Check if discussing a topic
     */
    public boolean isDiscussingTopic(String topic) {
        if (conversationHistory.isEmpty()) {
            return false;
        }
        
        List<ConversationTurn> recent = getRecentHistory(3);
        for (ConversationTurn turn : recent) {
            if (turn.userInput.toLowerCase().contains(topic.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get current state
     */
    public ConversationState getCurrentState() {
        return currentState;
    }
    
    /**
     * Set current state
     */
    public void setCurrentState(ConversationState state) {
        this.currentState = state;
        Log.d(TAG, "Conversation state changed: " + state);
    }
    
    /**
     * Set user name
     */
    public void setUserName(String name) {
        this.userName = name;
    }
    
    /**
     * Get user name
     */
    public String getUserName() {
        return userName != null ? userName : "朋友";
    }
    
    /**
     * Clear conversation history
     */
    public void clearHistory() {
        conversationHistory.clear();
        Log.d(TAG, "Conversation history cleared");
    }
    
    /**
     * Get conversation statistics
     */
    public ConversationStats getStats() {
        int totalTurns = conversationHistory.size();
        int commandCount = 0;
        int chatCount = 0;
        
        for (ConversationTurn turn : conversationHistory) {
            if (turn.isCommand) {
                commandCount++;
            } else {
                chatCount++;
            }
        }
        
        return new ConversationStats(totalTurns, commandCount, chatCount);
    }
    
    /**
     * Conversation statistics
     */
    public static class ConversationStats {
        public int totalTurns;
        public int commandCount;
        public int chatCount;
        
        public ConversationStats(int totalTurns, int commandCount, int chatCount) {
            this.totalTurns = totalTurns;
            this.commandCount = commandCount;
            this.chatCount = chatCount;
        }
    }
}

