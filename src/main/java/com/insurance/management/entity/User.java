package com.insurance.management.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Entity - Maps to the app_users table in DataSync database
 * This entity follows the existing database schema from Node.js implementation
 */
@Entity
@Table(name = "app_users")
@Data
@EqualsAndHashCode(callSuper = false)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Authentication fields
    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Personal information
    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "mobile_number")
    private String mobileNumber;

    // Location information
    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "location_state")
    private String locationState;

    // Role and permissions
    @Column(name = "user_role", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole userRole = UserRole.USER;

    // Account status
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "is_locked")
    private Boolean isLocked = false;

    // Security fields
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "max_failed_attempts")
    private Integer maxFailedAttempts = 5;

    @Column(name = "last_login")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_password_change")
    private LocalDateTime passwordChangedAt;

    @Column(name = "locked_until")
    private LocalDateTime accountLockedUntil;

    // Email verification
    @Column(name = "verification_token")
    private String emailVerificationToken;

    // Password reset
    @Column(name = "reset_password_token")
    private String passwordResetToken;

    @Column(name = "reset_token_expires")
    private LocalDateTime passwordResetExpiresAt;

    // Audit fields
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    // Profile information
    @Column(name = "profile_picture_url")
    private String profileImageUrl;

    @Column(name = "timezone")
    private String timezone = "UTC";

    @Column(name = "preferred_language")
    private String language = "en";

    // Additional fields from actual database schema
    @Column(name = "salt")
    private String salt;

    @Column(name = "permissions")
    private String permissions;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "fcm_token")
    private String fcmToken;

    // One-to-many relationship with customers
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private List<Customer> assignedCustomers;

    /**
     * User Role Enum - Based on the role system from Node.js implementation
     */
    public enum UserRole {
        ADMIN("admin"),
        SUPERVISOR("supervisor"),
        MANAGER("manager"),
        TEAM_LEAD("team_lead"),
        USER("user"),
        MOBILE_USER("mobile_user");

        private final String value;

        UserRole(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * Check if this role has administrative privileges
         */
        public boolean isAdmin() {
            return this == ADMIN;
        }

        /**
         * Check if this role has supervisor privileges
         */
        public boolean isSupervisor() {
            return this == ADMIN || this == SUPERVISOR;
        }

        /**
         * Check if this role is a mobile user
         */
        public boolean isMobileUser() {
            return this == MOBILE_USER;
        }

        /**
         * Convert string value to enum
         */
        public static UserRole fromValue(String value) {
            if (value == null) {
                return USER;
            }
            // Handle both database format (uppercase) and internal format (lowercase)
            String normalizedValue = value.toLowerCase().trim();
            for (UserRole role : values()) {
                if (role.value.equals(normalizedValue) || role.name().equalsIgnoreCase(value)) {
                    return role;
                }
            }
            return USER; // Default fallback
        }
    }

    /**
     * Pre-persist callback to set default values
     */
    @PrePersist
    protected void onCreate() {
        if (userRole == null) {
            userRole = UserRole.USER;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (isVerified == null) {
            isVerified = false;
        }
        if (isLocked == null) {
            isLocked = false;
        }
        if (failedLoginAttempts == null) {
            failedLoginAttempts = 0;
        }
        if (maxFailedAttempts == null) {
            maxFailedAttempts = 5;
        }
        if (passwordChangedAt == null) {
            passwordChangedAt = LocalDateTime.now();
        }
    }

    /**
     * Get full user name
     */
    public String getFullName() {
        StringBuilder name = new StringBuilder();
        if (firstName != null) {
            name.append(firstName);
        }
        if (lastName != null) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(lastName);
        }
        return name.toString();
    }

    /**
     * Check if account is locked
     */
    public boolean isAccountLocked() {
        if (!isLocked) {
            return false;
        }
        if (accountLockedUntil == null) {
            return true;
        }
        return LocalDateTime.now().isBefore(accountLockedUntil);
    }

    /**
     * Check if account is active and not locked
     */
    public boolean isAccountActive() {
        return isActive && !isAccountLocked();
    }

    /**
     * Increment failed login attempts
     */
    public void incrementFailedLoginAttempts() {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxFailedAttempts) {
            isLocked = true;
            // Lock for 30 minutes
            accountLockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }

    /**
     * Reset failed login attempts (on successful login)
     */
    public void resetFailedLoginAttempts() {
        failedLoginAttempts = 0;
        isLocked = false;
        accountLockedUntil = null;
    }

    /**
     * Update last login information
     */
    public void updateLastLogin(String ipAddress) {
        lastLoginAt = LocalDateTime.now();
        resetFailedLoginAttempts();
    }

    /**
     * Check if email verification token is valid
     */
    public boolean isEmailVerificationTokenValid(String token) {
        if (emailVerificationToken == null || token == null) {
            return false;
        }
        return emailVerificationToken.equals(token);
    }

    /**
     * Check if password reset token is valid
     */
    public boolean isPasswordResetTokenValid(String token) {
        if (passwordResetToken == null || token == null) {
            return false;
        }
        if (passwordResetExpiresAt != null && 
            LocalDateTime.now().isAfter(passwordResetExpiresAt)) {
            return false;
        }
        return passwordResetToken.equals(token);
    }

    /**
     * Get count of assigned customers
     */
    public int getAssignedCustomerCount() {
        return assignedCustomers != null ? assignedCustomers.size() : 0;
    }
}
