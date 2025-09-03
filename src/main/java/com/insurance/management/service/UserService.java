package com.insurance.management.service;

import com.insurance.management.entity.User;
import com.insurance.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * User Service - Business logic layer for User operations
 * Based on the existing Node.js server implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticate user (for mobile and web login)
     * Matches the Node.js authentication logic EXACTLY:
     * - Direct string comparison: password_hash = @password_hash
     * - No BCrypt or hashing, just plain string equality
     * - is_active = 1 AND is_locked = 0
     */
    public Optional<User> authenticateUser(String identifier, String password) {
        // Find user by username or email (same as Node.js)
        Optional<User> userOptional = userRepository.findByUsernameOrEmail(identifier);
        
        if (userOptional.isEmpty()) {
            log.warn("🔐 Authentication failed - User not found: {}", identifier);
            return Optional.empty();
        }
        
        User user = userOptional.get();
        
        // Check if account is active and not locked
        if (!user.isAccountActive()) {
            log.warn("🔒 Authentication failed - Account inactive or locked: {}", identifier);
            return Optional.empty();
        }
        
        // Verify password - Node.js does direct string comparison: password_hash = @password_hash
        // Match the exact Node.js authentication logic (no hashing, just string comparison)
        String storedPassword = user.getPasswordHash();
        if (storedPassword == null || !storedPassword.equals(password)) {
            log.warn("🔐 Authentication failed - Invalid password for user: {}", identifier);
            user.incrementFailedLoginAttempts();
            userRepository.save(user);
            return Optional.empty();
        }
        
        // Successful authentication
        log.info("✅ User authenticated successfully: {}", user.getUsername());
        return Optional.of(user);
    }

    /**
     * Update user's last login information
     * Called after successful authentication
     */
    public void updateLastLogin(User user, String ipAddress) {
        user.updateLastLogin(ipAddress);
        userRepository.save(user);
        log.info("📝 Updated last login for user: {} from IP: {}", user.getUsername(), ipAddress);
    }

    /**
     * Create new user
     */
    public User createUser(String username, String email, String password, String firstName, 
                          String lastName, String mobileNumber, String locationCity, 
                          String locationState, User.UserRole role) {
        
        // Check if username or email already exists
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists: " + email);
        }
        
        // Create new user
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMobileNumber(mobileNumber);
        user.setLocationCity(locationCity);
        user.setLocationState(locationState);
        user.setUserRole(role != null ? role : User.UserRole.USER);
        user.setCreatedBy("SYSTEM");
        
        User savedUser = userRepository.save(user);
        log.info("✅ Created new user: {} with role: {}", savedUser.getUsername(), savedUser.getUserRole());
        
        return savedUser;
    }

    /**
     * Find user by ID
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Find user by username
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Get all active users
     */
    public List<User> getActiveUsers() {
        return userRepository.findActiveUsers();
    }

    /**
     * Get active mobile users (for mobile API authentication)
     */
    public List<User> getActiveMobileUsers() {
        return userRepository.findActiveMobileUsers(User.UserRole.MOBILE_USER);
    }

    /**
     * Get users by state
     */
    public List<User> getUsersByState(String state) {
        return userRepository.findByLocationState(state);
    }

    /**
     * Get users by role
     */
    public List<User> getUsersByRole(User.UserRole role) {
        return userRepository.findByUserRole(role);
    }

    /**
     * Get users by role and state
     */
    public List<User> getUsersByRoleAndState(User.UserRole role, String state) {
        return userRepository.findByUserRoleAndLocationState(role, state);
    }

    /**
     * Get all users with filtering and pagination
     * Matches the admin API requirements
     */
    public Page<User> getAllUsers(String role, String state, String search, Pageable pageable) {
        if (search != null && !search.trim().isEmpty()) {
            return userRepository.findUsersWithFilters(role, state, search.trim(), pageable);
        } else if (role != null && state != null) {
            return userRepository.findByUserRoleAndLocationState(User.UserRole.fromValue(role), state, pageable);
        } else if (role != null) {
            return userRepository.findByUserRole(User.UserRole.fromValue(role), pageable);
        } else if (state != null) {
            return userRepository.findByLocationState(state, pageable);
        } else {
            return userRepository.findAll(pageable);
        }
    }

    /**
     * Get users with assigned customers
     */
    public List<User> getUsersWithAssignedCustomers() {
        return userRepository.findUsersWithAssignedCustomers();
    }

    /**
     * Search users by name, email, or username
     */
    public List<User> searchUsers(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of();
        }
        return userRepository.searchUsers(searchTerm.trim());
    }

    /**
     * Update user password
     */
    public boolean updatePassword(Long userId, String currentPassword, String newPassword) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return false;
        }
        
        User user = userOptional.get();
        
        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("🔐 Password update failed - Invalid current password for user: {}", user.getUsername());
            return false;
        }
        
        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("✅ Password updated successfully for user: {}", user.getUsername());
        return true;
    }

    /**
     * Lock/Unlock user account
     */
    public void lockUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsLocked(true);
            user.setAccountLockedUntil(null); // Permanent lock
            userRepository.save(user);
            log.info("🔒 User account locked: {}", user.getUsername());
        });
    }

    public void unlockUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsLocked(false);
            user.setAccountLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            log.info("🔓 User account unlocked: {}", user.getUsername());
        });
    }

    /**
     * Activate/Deactivate user account
     */
    public void activateUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsActive(true);
            userRepository.save(user);
            log.info("✅ User account activated: {}", user.getUsername());
        });
    }

    public void deactivateUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsActive(false);
            userRepository.save(user);
            log.info("❌ User account deactivated: {}", user.getUsername());
        });
    }

    /**
     * Verify user email
     */
    public boolean verifyEmail(String token) {
        Optional<User> userOptional = userRepository.findByEmailVerificationToken(token);
        if (userOptional.isEmpty()) {
            return false;
        }
        
        User user = userOptional.get();
        if (!user.isEmailVerificationTokenValid(token)) {
            return false;
        }
        
        user.setIsVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
        
        log.info("✅ Email verified successfully for user: {}", user.getUsername());
        return true;
    }

    /**
     * Generate email verification token
     */
    public String generateEmailVerificationToken(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return null;
        }
        
        User user = userOptional.get();
        String token = UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        userRepository.save(user);
        
        log.info("📧 Email verification token generated for user: {}", user.getUsername());
        return token;
    }

    /**
     * Generate password reset token
     */
    public String generatePasswordResetToken(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return null;
        }
        
        User user = userOptional.get();
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(24)); // 24 hour expiry
        userRepository.save(user);
        
        log.info("🔑 Password reset token generated for user: {}", user.getUsername());
        return token;
    }

    /**
     * Reset password using token
     */
    public boolean resetPassword(String token, String newPassword) {
        Optional<User> userOptional = userRepository.findByPasswordResetToken(token);
        if (userOptional.isEmpty()) {
            return false;
        }
        
        User user = userOptional.get();
        if (!user.isPasswordResetTokenValid(token)) {
            return false;
        }
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
        
        log.info("🔑 Password reset successfully for user: {}", user.getUsername());
        return true;
    }

    /**
     * Clean up expired tokens and unlock expired account locks
     */
    public void cleanupExpiredTokensAndLocks() {
        LocalDateTime now = LocalDateTime.now();
        
        // Clean up expired email verification tokens
        List<User> expiredEmailTokens = userRepository.findUsersWithExpiredEmailVerificationTokens(now.minusHours(24));
        expiredEmailTokens.forEach(user -> {
            user.setEmailVerificationToken(null);
        });
        if (!expiredEmailTokens.isEmpty()) {
            userRepository.saveAll(expiredEmailTokens);
            log.info("🧹 Cleaned up {} expired email verification tokens", expiredEmailTokens.size());
        }
        
        // Clean up expired password reset tokens
        List<User> expiredPasswordTokens = userRepository.findUsersWithExpiredPasswordResetTokens(now);
        expiredPasswordTokens.forEach(user -> {
            user.setPasswordResetToken(null);
            user.setPasswordResetExpiresAt(null);
        });
        if (!expiredPasswordTokens.isEmpty()) {
            userRepository.saveAll(expiredPasswordTokens);
            log.info("🧹 Cleaned up {} expired password reset tokens", expiredPasswordTokens.size());
        }
        
        // Unlock accounts with expired lock times
        List<User> expiredLocks = userRepository.findUsersWithExpiredLocks(now);
        expiredLocks.forEach(user -> {
            user.setIsLocked(false);
            user.setAccountLockedUntil(null);
            user.setFailedLoginAttempts(0);
        });
        if (!expiredLocks.isEmpty()) {
            userRepository.saveAll(expiredLocks);
            log.info("🔓 Unlocked {} accounts with expired lock times", expiredLocks.size());
        }
    }

    /**
     * Get user statistics
     */
    public List<Object[]> getUserStatisticsByState() {
        return userRepository.getUserStatisticsByState();
    }

    public List<Object[]> getUserStatisticsByRole() {
        return userRepository.getUserStatisticsByRole();
    }

    public long getTotalUserCount() {
        return userRepository.getTotalUserCount();
    }

    public long getActiveUserCount() {
        return userRepository.countActiveUsers();
    }

    /**
     * Get daily statistics
     */
    public List<Object[]> getDailyUserRegistrationStats(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        return userRepository.getDailyUserRegistrationStats(fromDate);
    }

    public List<Object[]> getDailyUserLoginStats(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        return userRepository.getDailyUserLoginStats(fromDate);
    }
}
