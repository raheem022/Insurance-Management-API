package com.insurance.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Customer DTOs for API communication
 * Based on the Node.js API response structure
 */
public class CustomerDTO {

    /**
     * Customer Response DTO - matches Node.js API response format
     */
    @Data
    public static class Response {
        private Long id;
        
        @JsonProperty("firstname")
        private String firstName;
        
        @JsonProperty("lastname") 
        private String lastName;
        
        @JsonProperty("mobilenumber")
        private String mobileNumber;
        
        @JsonProperty("customeremailaddress")
        private String customerEmailAddress;
        
        private String city;
        private String state;
        
        @JsonProperty("pincode")
        private String pinCode;
        
        @JsonProperty("addressline1")
        private String addressLine1;
        
        // Vehicle information
        @JsonProperty("registrationnum")
        private String registrationNumber;
        
        @JsonProperty("vehiclemake")
        private String vehicleMake;
        
        @JsonProperty("vehmodel")
        private String vehicleModel;
        
        @JsonProperty("modelvariant")
        private String modelVariant;
        
        private String color;
        
        @JsonProperty("registrationdate")
        private LocalDate registrationDate;
        
        // Insurance information
        @JsonProperty("previousinsname")
        private String previousInsuranceName;
        
        @JsonProperty("previnsno")
        private String previousInsuranceNumber;
        
        // Status fields
        @JsonProperty("customer_status")
        private String customerStatus;
        
        @JsonProperty("is_closed")
        private Boolean isClosed;
        
        @JsonProperty("last_status_updated")
        private LocalDateTime lastStatusUpdated;
        
        @JsonProperty("reminder_date")
        private LocalDateTime reminderDate;
        
        // Assignment info
        @JsonProperty("assigned_to")
        private Long assignedTo;
        
        @JsonProperty("assigned_at")
        private LocalDateTime assignedAt;
        
        @JsonProperty("status_updated_by")
        private Long statusUpdatedBy;
        
        private String notes;
        
        @JsonProperty("last_contact_date")
        private LocalDateTime lastContactDate;
        
        // Audit fields
        @JsonProperty("created_at")
        private LocalDateTime createdAt;
        
        @JsonProperty("updated_at")
        private LocalDateTime updatedAt;
        
        // Additional fields for mobile API compatibility
        @JsonProperty("allocation_status")
        private String allocationStatus = "ASSIGNED";
        
        @JsonProperty("allocation_notes")
        private String allocationNotes = "Assigned via API";
    }

    /**
     * Customer Status Update Request DTO
     * Matches: PATCH /api/mobile/customers/:id/status request body
     */
    @Data
    public static class StatusUpdateRequest {
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^(active|renewed|not_interested|not_reachable|follow_up)$", 
                message = "Invalid status. Must be one of: active, renewed, not_interested, not_reachable, follow_up")
        private String status;
        
        private String notes;
        
        @JsonProperty("reminder_date")
        private LocalDateTime reminderDate;
    }

    /**
     * Individual Customer Submission Request DTO
     * For the NEW endpoint: POST /api/mobile/customers/submit-individual
     */
    @Data
    public static class IndividualSubmissionRequest {
        @NotNull(message = "User ID is required")
        @JsonProperty("userId")
        private Long userId;
        
        @NotNull(message = "Customer ID is required")
        @JsonProperty("customerId")
        private Long customerId;
        
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^(active|renewed|not_interested|not_reachable|follow_up)$", 
                message = "Invalid status. Must be one of: active, renewed, not_interested, not_reachable, follow_up")
        private String status;
        
        @JsonProperty("followUpDate")
        private LocalDateTime followUpDate;
        
        private String notes;
        
        @JsonProperty("updatedBy")
        private String updatedBy;
        
        @JsonProperty("unassignAfterSubmission")
        private Boolean unassignAfterSubmission = true;
    }

    /**
     * Customer Search Request DTO
     */
    @Data
    public static class SearchRequest {
        private String searchTerm;
        private String state;
        private String city;
        private String status;
        private Boolean assigned;
        private Integer page = 1;
        private Integer size = 50;
    }

    /**
     * Customer Assignment Request DTO
     */
    @Data
    public static class AssignmentRequest {
        @NotNull(message = "User ID is required")
        @JsonProperty("userId")
        private Long userId;
        
        @NotNull(message = "Customer IDs are required")
        @JsonProperty("customerIds")
        private java.util.List<Long> customerIds;
        
        @JsonProperty("assignedBy")
        private Long assignedBy;
    }

    /**
     * Paginated Customer Response DTO
     * Matches Node.js paginated API response format
     */
    @Data
    public static class PaginatedResponse {
        private boolean success = true;
        private CustomerData data;
        
        @Data
        public static class CustomerData {
            private java.util.List<Response> customers;
            private Pagination pagination;
            private UserInfo userInfo;
            private FilterInfo filterInfo;
            private java.util.List<StatusBreakdown> statusBreakdown;
            private SystemInfo systemInfo;
        }
        
        @Data
        public static class Pagination {
            @JsonProperty("current_page")
            private Integer currentPage;
            
            @JsonProperty("page_size") 
            private Integer pageSize;
            
            @JsonProperty("total_count")
            private Long totalCount;
            
            @JsonProperty("total_pages")
            private Integer totalPages;
        }
        
        @Data
        public static class UserInfo {
            private String username;
            
            @JsonProperty("user_id")
            private Long userId;
            
            private String state;
        }
        
        @Data
        public static class FilterInfo {
            @JsonProperty("include_closed")
            private Boolean includeClosed;
            
            private String showing;
            
            @JsonProperty("closed_statuses")
            private java.util.List<String> closedStatuses = 
                java.util.List.of("active", "renewed", "not_interested");
            
            @JsonProperty("open_statuses")
            private java.util.List<String> openStatuses = 
                java.util.List.of("not_reachable", "follow_up", "not_started");
        }
        
        @Data
        public static class StatusBreakdown {
            @JsonProperty("customer_status")
            private String customerStatus;
            
            @JsonProperty("is_closed")
            private Boolean isClosed;
            
            private Long count;
        }
        
        @Data
        public static class SystemInfo {
            @JsonProperty("status_rules")
            private StatusRules statusRules;
            
            @Data
            public static class StatusRules {
                private String closed = "active, renewed, not_interested → removed from home list";
                private String open = "not_reachable, follow_up → remain in home list";
                private String reminder = "follow_up customers with future reminder dates are hidden until due";
            }
        }
    }

    /**
     * Customer Status Update Response DTO
     * Matches Node.js PATCH /api/mobile/customers/:id/status response
     */
    @Data
    public static class StatusUpdateResponse {
        private boolean success = true;
        private StatusUpdateData data;
        
        @Data
        public static class StatusUpdateData {
            @JsonProperty("customer_id")
            private Long customerId;
            
            private String status;
            
            @JsonProperty("is_closed")
            private Boolean isClosed;
            
            @JsonProperty("reminder_date")
            private LocalDateTime reminderDate;
            
            @JsonProperty("updated_by")
            private String updatedBy;
            
            @JsonProperty("main_db_updated")
            private Boolean mainDbUpdated = true;
            
            @JsonProperty("state_db_updated")
            private Boolean stateDbUpdated = false;
            
            @JsonProperty("database_used")
            private String databaseUsed = "main";
            
            @JsonProperty("closure_info")
            private ClosureInfo closureInfo;
            
            @Data
            public static class ClosureInfo {
                @JsonProperty("closed_statuses")
                private java.util.List<String> closedStatuses = 
                    java.util.List.of("active", "renewed", "not_interested");
                
                @JsonProperty("open_statuses")
                private java.util.List<String> openStatuses = 
                    java.util.List.of("not_reachable", "follow_up");
                
                @JsonProperty("action_taken")
                private String actionTaken;
            }
        }
    }

    /**
     * Individual Submission Response DTO
     * For the NEW endpoint: POST /api/mobile/customers/submit-individual
     */
    @Data
    public static class IndividualSubmissionResponse {
        private boolean success = true;
        private String message;
        private IndividualSubmissionData data;
        
        @Data
        public static class IndividualSubmissionData {
            @JsonProperty("customer_id")
            private Long customerId;
            
            private String status;
            
            @JsonProperty("is_closed")
            private Boolean isClosed = true; // Always true for individual submissions
            
            @JsonProperty("updated_by")
            private String updatedBy;
            
            @JsonProperty("unassigned")
            private Boolean unassigned = true;
            
            @JsonProperty("moved_to_submissions")
            private Boolean movedToSubmissions = true;
        }
    }

    /**
     * Analytics Response DTO
     * For the mobile analytics endpoint: GET /api/mobile/analytics
     */
    @Data
    public static class AnalyticsResponse {
        private boolean success = true;
        private String message;
        private AnalyticsData data;
    }
    
    /**
     * Analytics Data DTO
     * Contains user analytics summary and status breakdown
     */
    @Data
    public static class AnalyticsData {
        private AnalyticsSummary summary;
        private StatusBreakdown statusBreakdown;
        private java.util.List<DailyActivity> dailyActivity;
        
        @Data
        public static class AnalyticsSummary {
            @JsonProperty("today_count")
            private int todayCount;
            
            @JsonProperty("week_count")
            private int weekCount;
            
            @JsonProperty("month_count")
            private int monthCount;
            
            @JsonProperty("total_assigned")
            private int totalAssigned;
            
            @JsonProperty("completed_count")
            private int completedCount;
            
            @JsonProperty("completion_rate")
            private double completionRate;
        }
        
        @Data
        public static class StatusBreakdown {
            private int active;
            private int renewed;
            
            @JsonProperty("not_interested")
            private int notInterested;
            
            @JsonProperty("not_reachable")
            private int notReachable;
            
            @JsonProperty("follow_up")
            private int followUp;
        }
        
        @Data
        public static class DailyActivity {
            private String date;
            private int count;
        }
    }

    /**
     * Error Response DTO
     */
    @Data
    public static class ErrorResponse {
        private boolean success = false;
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
