package com.insurance.management.repository;

import com.insurance.management.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * User Repository - Data access layer for User entity
 * Based on the existing Node.js authentication and user management
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username for authentication
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email for authentication
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by username or email
     */
    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    /**
     * Check if username already exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email already exists
     */
    boolean existsByEmail(String email);

    /**
     * Find users by role
     */
    List<User> findByUserRole(User.UserRole userRole);

    /**
     * Find users by state
     */
    List<User> findByLocationState(String locationState);

    /**
     * Find users by role and state
     */
    List<User> findByUserRoleAndLocationState(User.UserRole userRole, String locationState);

    /**
     * Find users by role with pagination
     */
    Page<User> findByUserRole(User.UserRole userRole, Pageable pageable);

    /**
     * Find users by state with pagination
     */
    Page<User> findByLocationState(String locationState, Pageable pageable);

    /**
     * Find users by role and state with pagination
     */
    Page<User> findByUserRoleAndLocationState(User.UserRole userRole, String locationState, Pageable pageable);

    /**
     * Find users with advanced filtering
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:role IS NULL OR u.userRole = :role) AND " +
           "(:state IS NULL OR u.locationState = :state) AND " +
           "(:search IS NULL OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findUsersWithFilters(
            @Param("role") String role,
            @Param("state") String state,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Find active users only
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.isLocked = false")
    List<User> findActiveUsers();

    /**
     * Find active users by role
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.isLocked = false AND u.userRole = :role")
    List<User> findActiveUsersByRole(@Param("role") User.UserRole role);

    /**
     * Find active mobile users (for mobile API)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.isLocked = false AND u.userRole = :role")
    List<User> findActiveMobileUsers(@Param("role") User.UserRole role);

    /**
     * Find user by email verification token
     */
    Optional<User> findByEmailVerificationToken(String token);

    /**
     * Find user by password reset token
     */
    Optional<User> findByPasswordResetToken(String token);

    /**
     * Find users who haven't logged in recently (inactive users)
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt < :cutoffDate OR u.lastLoginAt IS NULL")
    List<User> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Get users with assigned customers
     */
    @Query("SELECT DISTINCT u FROM User u WHERE u.id IN (SELECT c.assignedTo FROM Customer c WHERE c.assignedTo IS NOT NULL)")
    List<User> findUsersWithAssignedCustomers();

    /**
     * Get user statistics by state
     */
    @Query("SELECT u.locationState, COUNT(u) as userCount, " +
           "SUM(CASE WHEN u.isActive = true THEN 1 ELSE 0 END) as activeUserCount " +
           "FROM User u WHERE u.locationState IS NOT NULL " +
           "GROUP BY u.locationState ORDER BY COUNT(u) DESC")
    List<Object[]> getUserStatisticsByState();

    /**
     * Get user statistics by role
     */
    @Query("SELECT u.userRole, COUNT(u) as userCount, " +
           "SUM(CASE WHEN u.isActive = true THEN 1 ELSE 0 END) as activeUserCount " +
           "FROM User u GROUP BY u.userRole ORDER BY COUNT(u) DESC")
    List<Object[]> getUserStatisticsByRole();

    /**
     * Find users created within date range
     */
    @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :fromDate AND :toDate ORDER BY u.createdAt DESC")
    Page<User> findUsersCreatedBetween(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    /**
     * Find users who logged in within date range
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt BETWEEN :fromDate AND :toDate ORDER BY u.lastLoginAt DESC")
    List<User> findUsersLoggedInBetween(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    /**
     * Count total active users
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true AND u.isLocked = false")
    long countActiveUsers();

    /**
     * Count total users
     */
    @Query("SELECT COUNT(u) FROM User u")
    long getTotalUserCount();

    /**
     * Count users by role
     */
    long countByUserRole(User.UserRole role);

    /**
     * Find users with failed login attempts above threshold
     */
    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts >= u.maxFailedAttempts")
    List<User> findUsersWithHighFailedLogins();

    /**
     * Find locked users
     */
    @Query("SELECT u FROM User u WHERE u.isLocked = true")
    List<User> findLockedUsers();

    /**
     * Find users whose account lock has expired
     */
    @Query("SELECT u FROM User u WHERE u.isLocked = true AND u.accountLockedUntil IS NOT NULL AND u.accountLockedUntil < :currentTime")
    List<User> findUsersWithExpiredLocks(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find unverified users
     */
    @Query("SELECT u FROM User u WHERE u.isVerified = false")
    List<User> findUnverifiedUsers();

    /**
     * Find users with expired email verification tokens
     */
    @Query("SELECT u FROM User u WHERE u.emailVerificationToken IS NOT NULL")
    List<User> findUsersWithExpiredEmailVerificationTokens(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find users with expired password reset tokens
     */
    @Query("SELECT u FROM User u WHERE u.passwordResetToken IS NOT NULL " +
           "AND u.passwordResetExpiresAt IS NOT NULL " +
           "AND u.passwordResetExpiresAt < :currentTime")
    List<User> findUsersWithExpiredPasswordResetTokens(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Search users by name or email
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<User> searchUsers(@Param("searchTerm") String searchTerm);

    /**
     * Get daily user registration statistics
     */
    @Query("SELECT CAST(u.createdAt AS DATE) as date, COUNT(u) as count " +
           "FROM User u WHERE u.createdAt >= :fromDate " +
           "GROUP BY CAST(u.createdAt AS DATE) ORDER BY CAST(u.createdAt AS DATE) DESC")
    List<Object[]> getDailyUserRegistrationStats(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Get daily user login statistics
     */
    @Query("SELECT CAST(u.lastLoginAt AS DATE) as date, COUNT(u) as count " +
           "FROM User u WHERE u.lastLoginAt >= :fromDate " +
           "GROUP BY CAST(u.lastLoginAt AS DATE) ORDER BY CAST(u.lastLoginAt AS DATE) DESC")
    List<Object[]> getDailyUserLoginStats(@Param("fromDate") LocalDateTime fromDate);
}
