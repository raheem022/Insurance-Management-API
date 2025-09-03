package com.insurance.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Login response DTO matching Node.js authentication response format
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    
    private boolean success;
    private LoginData data;
    private String error;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginData {
        private UserInfo user;
        private String token;
        
        @JsonProperty("authenticated_from")
        private String authenticatedFrom;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        
        @JsonProperty("user_role")
        private String userRole;
        
        @JsonProperty("location_state")
        private String locationState;
        
        @JsonProperty("first_name")
        private String firstName;
        
        @JsonProperty("last_name")
        private String lastName;
        
        @JsonProperty("mobile_number")
        private String mobileNumber;
    }
}
