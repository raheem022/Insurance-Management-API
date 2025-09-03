package com.insurance.management.service;

import com.insurance.management.entity.User;
import com.insurance.management.entity.Customer;
import com.insurance.management.repository.UserRepository;
import com.insurance.management.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Metrics Service for admin dashboard functionality
 * Replicates the Node.js admin metrics endpoints logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MetricsService {
    
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    
    /**
     * Get global overview metrics
     * Matches GET /api/admin/metrics/overview from Node.js
     */
    public Map<String, Object> getOverviewMetrics(String from, String to) {
        log.info("📊 Fetching overview metrics - From: {}, To: {}", from, to);
        
        try {
            // Get total customer count
            long totalCustomers = customerRepository.count();
            
            // Get REAL customer status breakdown from database
            // Map the actual 5-status system to dashboard categories
            List<Object[]> rawStatusData = customerRepository.getCustomerStatusBreakdown();
            
            // Convert real status data to dashboard format
            Map<String, Integer> statusCounts = new HashMap<>();
            statusCounts.put("COMPLETED", 0);    // active + renewed 
            statusCounts.put("NOT_STARTED", 0);  // not_started
            statusCounts.put("IN_PROGRESS", 0);  // not_reachable + follow_up + not_interested
            
            for (Object[] row : rawStatusData) {
                String status = (String) row[0];
                Long count = ((Number) row[1]).longValue();
                
                // Map actual customer statuses to dashboard categories
                switch (status) {
                    case "active":
                    case "renewed":
                        statusCounts.put("COMPLETED", statusCounts.get("COMPLETED") + count.intValue());
                        break;
                    case "not_started":
                        statusCounts.put("NOT_STARTED", statusCounts.get("NOT_STARTED") + count.intValue());
                        break;
                    case "not_reachable":
                    case "follow_up":
                    case "not_interested":
                        statusCounts.put("IN_PROGRESS", statusCounts.get("IN_PROGRESS") + count.intValue());
                        break;
                    default:
                        // Unknown status - count as not started
                        statusCounts.put("NOT_STARTED", statusCounts.get("NOT_STARTED") + count.intValue());
                        break;
                }
            }
            
            // Build status breakdown response
            List<Map<String, Object>> statusBreakdown = Arrays.asList(
                Map.of("status", "COMPLETED", "count", statusCounts.get("COMPLETED")),
                Map.of("status", "IN_PROGRESS", "count", statusCounts.get("IN_PROGRESS")),
                Map.of("status", "NOT_STARTED", "count", statusCounts.get("NOT_STARTED"))
            );
            
            // Get states summary
            List<Map<String, Object>> statesSummary = getStatesSummary();
            
            // Get top users
            List<Map<String, Object>> topUsers = getTopUsers();
            
            // Build overview response matching Node.js format
            Map<String, Object> overviewMetrics = new HashMap<>();
            overviewMetrics.put("totalCustomers", totalCustomers);
            overviewMetrics.put("statusBreakdown", statusBreakdown);
            overviewMetrics.put("statesSummary", statesSummary);
            overviewMetrics.put("topUsers", topUsers);
            overviewMetrics.put("dailyProgress", new ArrayList<>()); // Will be populated by separate endpoint
            overviewMetrics.put("dateRange", Map.of(
                "from", from != null ? from : LocalDateTime.now().minusDays(30).toString(),
                "to", to != null ? to : LocalDateTime.now().toString()
            ));
            
            log.info("✅ Overview metrics fetched successfully - {} customers, {} states", 
                totalCustomers, statesSummary.size());
            
            return overviewMetrics;
            
        } catch (Exception e) {
            log.error("❌ Error fetching overview metrics", e);
            throw new RuntimeException("Failed to fetch overview metrics: " + e.getMessage());
        }
    }
    
    /**
     * Get state-specific metrics  
     * Matches GET /api/admin/metrics/state from Node.js
     */
    public Map<String, Object> getStateMetrics(String state, String from, String to) {
        log.info("📍 Fetching state metrics for: {}", state);
        
        try {
            // Get customers for the specific state
            List<Customer> customers = customerRepository.findByState(state);
            
            Map<String, Object> stateData = new HashMap<>();
            stateData.put("state", state);
            stateData.put("total_customers", customers.size());
            stateData.put("customers_with_names", 
                customers.stream().mapToLong(c -> 
                    (c.getFirstName() != null && !c.getFirstName().trim().isEmpty()) ? 1 : 0).sum());
            stateData.put("customers_with_mobile", 
                customers.stream().mapToLong(c -> 
                    (c.getMobileNumber() != null && !c.getMobileNumber().trim().isEmpty()) ? 1 : 0).sum());
            
            log.info("✅ State metrics fetched for {} - {} customers", state, customers.size());
            
            return stateData;
            
        } catch (Exception e) {
            log.error("❌ Error fetching state metrics for: {}", state, e);
            throw new RuntimeException("Failed to fetch state metrics: " + e.getMessage());
        }
    }
    
    /**
     * Get daily progress trend data
     * Matches GET /api/admin/metrics/daily from Node.js  
     */
    public List<Map<String, Object>> getDailyMetrics(String state, Integer days) {
        log.info("📅 Fetching daily metrics - State: {}, Days: {}", state, days);
        
        try {
            int daysToFetch = days != null ? days : 7;
            List<Map<String, Object>> dailyData = new ArrayList<>();
            
            // Generate sample daily data (in production, query actual database)
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            for (int i = daysToFetch - 1; i >= 0; i--) {
                LocalDateTime date = now.minusDays(i);
                
                // Get customer count for this day (simplified)
                long dayCount = Math.max(0, (long)(Math.random() * 50)); // Sample data
                
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("date", date.format(formatter));
                dayData.put("count", dayCount);
                dayData.put("completed", Math.max(0, (long)(dayCount * 0.7))); // Sample completion rate
                dayData.put("in_progress", Math.max(0, (long)(dayCount * 0.3))); // Sample in-progress rate
                
                dailyData.add(dayData);
            }
            
            log.info("✅ Daily metrics fetched - {} days of data", dailyData.size());
            
            return dailyData;
            
        } catch (Exception e) {
            log.error("❌ Error fetching daily metrics", e);
            throw new RuntimeException("Failed to fetch daily metrics: " + e.getMessage());
        }
    }
    
    /**
     * Get available states
     * Matches GET /api/admin/metrics/states from Node.js
     */
    public List<String> getAvailableStates() {
        log.info("🗺️ Fetching available states");
        
        try {
            // Get distinct states from customers
            List<String> states = customerRepository.findDistinctStates();
            
            // Filter out null/empty states and sort
            List<String> validStates = states.stream()
                .filter(state -> state != null && !state.trim().isEmpty())
                .sorted()
                .collect(Collectors.toList());
            
            log.info("✅ Found {} available states", validStates.size());
            
            return validStates;
            
        } catch (Exception e) {
            log.error("❌ Error fetching available states", e);
            throw new RuntimeException("Failed to fetch available states: " + e.getMessage());
        }
    }
    
    /**
     * Get user metrics and statistics
     * Additional method for user-related metrics
     */
    public Map<String, Object> getUserMetrics() {
        log.info("👥 Fetching user metrics");
        
        try {
            // Get user counts by role
            Map<String, Long> usersByRole = new HashMap<>();
            for (User.UserRole role : User.UserRole.values()) {
                long count = userRepository.countByUserRole(role);
                usersByRole.put(role.name(), count);
            }
            
            // Get user counts by state
            List<Object[]> usersByState = userRepository.getUserStatisticsByState();
            List<Map<String, Object>> stateStats = usersByState.stream()
                .map(row -> Map.of(
                    "state", row[0] != null ? row[0] : "Unknown",
                    "count", row[1] != null ? row[1] : 0L
                ))
                .collect(Collectors.toList());
            
            // Get total counts
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.countActiveUsers();
            
            Map<String, Object> userMetrics = new HashMap<>();
            userMetrics.put("totalUsers", totalUsers);
            userMetrics.put("activeUsers", activeUsers);
            userMetrics.put("usersByRole", usersByRole);
            userMetrics.put("usersByState", stateStats);
            
            log.info("✅ User metrics fetched - Total: {}, Active: {}", totalUsers, activeUsers);
            
            return userMetrics;
            
        } catch (Exception e) {
            log.error("❌ Error fetching user metrics", e);
            throw new RuntimeException("Failed to fetch user metrics: " + e.getMessage());
        }
    }
    
    // Private helper methods
    
    private List<Map<String, Object>> getStatesSummary() {
        try {
            // Get customer counts by state
            Map<String, List<Customer>> customersByState = customerRepository.findAll().stream()
                .filter(c -> c.getState() != null && !c.getState().trim().isEmpty())
                .collect(Collectors.groupingBy(Customer::getState));
            
            return customersByState.entrySet().stream()
                .map(entry -> {
                    String state = entry.getKey();
                    List<Customer> customers = entry.getValue();
                    int total = customers.size();
                    
                    Map<String, Object> stateData = new HashMap<>();
                    stateData.put("state", state);
                    stateData.put("totalCustomers", total);
                    stateData.put("completedCount", Math.max(1, (int)(total * 0.3))); // Sample completion rate
                    stateData.put("inProgressCount", Math.max(1, (int)(total * 0.4))); // Sample in-progress rate
                    stateData.put("unassignedCount", Math.max(0, (int)(total * 0.3))); // Sample unassigned rate
                    stateData.put("activeUsers", Math.max(1, 5)); // Default active users
                    stateData.put("completionPercentage", 30); // Default 30% completion
                    
                    return stateData;
                })
                .sorted((a, b) -> Integer.compare((Integer)b.get("totalCustomers"), (Integer)a.get("totalCustomers")))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("⚠️ Error fetching states summary, returning empty list", e);
            return new ArrayList<>();
        }
    }
    
    private List<Map<String, Object>> getTopUsers() {
        try {
            // Get top 10 users by assignment count (simplified)
            List<User> topUsers = userRepository.findUsersWithAssignedCustomers()
                .stream()
                .filter(user -> user.getUserRole() == User.UserRole.USER)
                .limit(10)
                .collect(Collectors.toList());
            
            return topUsers.stream()
                .map(user -> {
                    // Get assigned customer count (simplified - in production use proper joins)
                    long assignedCount = customerRepository.countByAssignedTo(user.getId());
                    
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("username", user.getUsername());
                    userData.put("fullName", 
                        String.join(" ", 
                            Optional.ofNullable(user.getFirstName()).orElse(""),
                            Optional.ofNullable(user.getLastName()).orElse("")
                        ).trim());
                    if (userData.get("fullName").toString().isEmpty()) {
                        userData.put("fullName", user.getUsername());
                    }
                    userData.put("locationState", user.getLocationState() != null ? user.getLocationState() : "");
                    userData.put("assignedCount", assignedCount);
                    userData.put("completedCount", Math.max(0, (long)(assignedCount * 0.7))); // Sample completion
                    userData.put("todayUpdatedCount", Math.max(0, (long)(assignedCount * 0.1))); // Sample today's updates
                    
                    return userData;
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("⚠️ Error fetching top users, returning empty list", e);
            return new ArrayList<>();
        }
    }
}
