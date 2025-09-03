package com.insurance.management.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request DTO for both web and mobile authentication
 * Matches the Node.js login endpoints
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "PIN is required")
    private String pin;
    
    // For mobile login - state is required
    private String state;
}
