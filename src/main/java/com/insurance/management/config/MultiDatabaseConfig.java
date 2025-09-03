package com.insurance.management.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-Database Configuration Placeholder
 * Sets up foundation for connecting to multiple state databases similar to Node.js
 * 
 * TODO: This is a placeholder for future implementation of state database routing
 * The Node.js version connects to 4 databases:
 * - DataSync (main)
 * - DataSync_Karnataka  
 * - DataSync_TamilNadu
 * - DataSync_AndhraPradesh
 */
@Configuration
@ConfigurationProperties(prefix = "app.databases")
@Slf4j
public class MultiDatabaseConfig {
    
    // Database connection configurations
    private Map<String, DatabaseInfo> databases = new HashMap<>();
    
    public MultiDatabaseConfig() {
        log.info("🗄️ Multi-database configuration initialized");
        
        // Initialize placeholder database configurations
        initializeDatabaseConfigs();
    }
    
    /**
     * Initialize database configurations matching Node.js setup
     */
    private void initializeDatabaseConfigs() {
        
        // Main database (currently active)
        DatabaseInfo mainDb = new DatabaseInfo();
        mainDb.setName("DataSync");
        mainDb.setKey("main"); 
        mainDb.setActive(true);
        mainDb.setDescription("Main insurance management database");
        databases.put("main", mainDb);
        
        // Karnataka state database (placeholder)
        DatabaseInfo karnatakaDb = new DatabaseInfo();
        karnatakaDb.setName("DataSync_Karnataka");
        karnatakaDb.setKey("karnataka");
        karnatakaDb.setActive(false); // TODO: Enable when implemented
        karnatakaDb.setDescription("Karnataka state-specific database");
        databases.put("karnataka", karnatakaDb);
        
        // Tamil Nadu state database (placeholder)  
        DatabaseInfo tamilNaduDb = new DatabaseInfo();
        tamilNaduDb.setName("DataSync_TamilNadu");
        tamilNaduDb.setKey("tamilnadu");
        tamilNaduDb.setActive(false); // TODO: Enable when implemented
        tamilNaduDb.setDescription("Tamil Nadu state-specific database");
        databases.put("tamilnadu", tamilNaduDb);
        
        // Andhra Pradesh state database (placeholder)
        DatabaseInfo apDb = new DatabaseInfo();
        apDb.setName("DataSync_AndhraPradesh"); 
        apDb.setKey("andhrapradesh");
        apDb.setActive(false); // TODO: Enable when implemented
        apDb.setDescription("Andhra Pradesh state-specific database");
        databases.put("andhrapradesh", apDb);
        
        log.info("📋 Configured {} database connections ({} active)", 
            databases.size(), 
            databases.values().stream().mapToLong(db -> db.isActive() ? 1 : 0).sum());
    }
    
    /**
     * Get database key for a given state (matches Node.js logic)
     */
    public String getStateDbKey(String state) {
        if (state == null) return "main";
        
        String normalized = state.toLowerCase().replaceAll("[\\s\\-_]", "");
        
        switch (normalized) {
            case "andhrapradesh":
            case "ap":
                return "andhrapradesh";
            case "karnataka":
            case "ka":
                return "karnataka";
            case "tamilnadu":
            case "tn":
                return "tamilnadu";
            default:
                return "main";
        }
    }
    
    /**
     * Get database name for a given state key
     */
    public String getDatabaseName(String stateKey) {
        DatabaseInfo dbInfo = databases.get(stateKey);
        return dbInfo != null ? dbInfo.getName() : "DataSync";
    }
    
    /**
     * Check if a database is active
     */
    public boolean isDatabaseActive(String stateKey) {
        DatabaseInfo dbInfo = databases.get(stateKey);
        return dbInfo != null && dbInfo.isActive();
    }
    
    /**
     * Get all configured databases
     */
    public Map<String, DatabaseInfo> getDatabases() {
        return databases;
    }
    
    public void setDatabases(Map<String, DatabaseInfo> databases) {
        this.databases = databases;
    }
    
    /**
     * Database information class
     */
    public static class DatabaseInfo {
        private String name;
        private String key;
        private boolean active;
        private String description;
        private String connectionString; // TODO: Add when implementing actual connections
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getConnectionString() { return connectionString; }
        public void setConnectionString(String connectionString) { this.connectionString = connectionString; }
    }
}
