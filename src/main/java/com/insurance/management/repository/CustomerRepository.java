package com.insurance.management.repository;

import com.insurance.management.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Customer Repository - Data access layer for Customer entity
 * Based on the existing Node.js queries and database operations
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Find customers assigned to a specific user (OPEN customers only)
     * This matches the mobile API query from Node.js implementation
     */
    @Query("SELECT c FROM Customer c WHERE c.assignedTo = :userId " +
           "AND (c.isClosed = false OR c.isClosed IS NULL) " +
           "AND (c.customerStatusString != 'follow_up' OR c.reminderDate IS NULL OR c.reminderDate <= :currentTime) " +
           "ORDER BY CASE WHEN c.customerStatusString = 'follow_up' AND c.reminderDate IS NOT NULL THEN c.reminderDate ELSE c.lastStatusUpdated END DESC")
    Page<Customer> findOpenCustomersAssignedToUser(
            @Param("userId") Long userId, 
            @Param("currentTime") LocalDateTime currentTime, 
            Pageable pageable);

    /**
     * Find ALL customers assigned to a user (including closed ones)
     */
    @Query("SELECT c FROM Customer c WHERE c.assignedTo = :userId " +
           "ORDER BY CASE WHEN c.customerStatusString = 'follow_up' AND c.reminderDate IS NOT NULL THEN c.reminderDate ELSE c.lastStatusUpdated END DESC")
    Page<Customer> findAllCustomersAssignedToUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Count open customers assigned to a user
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.assignedTo = :userId " +
           "AND (c.isClosed = false OR c.isClosed IS NULL) " +
           "AND (c.customerStatusString != 'follow_up' OR c.reminderDate IS NULL OR c.reminderDate <= :currentTime)")
    long countOpenCustomersAssignedToUser(@Param("userId") Long userId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * Count all customers assigned to a user
     */
    long countByAssignedTo(Long userId);

    /**
     * Find customers due for follow-up
     */
    @Query("SELECT c FROM Customer c WHERE c.assignedTo = :userId " +
           "AND c.customerStatusString = 'follow_up' " +
           "AND c.reminderDate IS NOT NULL " +
           "AND c.reminderDate <= :futureDate " +
           "AND (c.isClosed = false OR c.isClosed IS NULL) " +
           "ORDER BY c.reminderDate ASC")
    Page<Customer> findFollowUpCustomersForUser(
            @Param("userId") Long userId,
            @Param("futureDate") LocalDateTime futureDate,
            Pageable pageable);

    /**
     * Count follow-up customers for a user
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.assignedTo = :userId " +
           "AND c.customerStatusString = 'follow_up' " +
           "AND c.reminderDate IS NOT NULL " +
           "AND c.reminderDate <= :futureDate " +
           "AND (c.isClosed = false OR c.isClosed IS NULL)")
    long countFollowUpCustomersForUser(@Param("userId") Long userId, @Param("futureDate") LocalDateTime futureDate);

    /**
     * Find unassigned customers available for assignment
     */
    @Query("SELECT c FROM Customer c WHERE c.assignedTo IS NULL " +
           "AND (c.isClosed = false OR c.isClosed IS NULL) " +
           "ORDER BY c.createdAt DESC")
    Page<Customer> findUnassignedCustomers(Pageable pageable);

    /**
     * Find unassigned customers by state
     */
    @Query("SELECT c FROM Customer c WHERE c.assignedTo IS NULL " +
           "AND (c.isClosed = false OR c.isClosed IS NULL) " +
           "AND c.state = :state " +
           "ORDER BY c.createdAt DESC")
    Page<Customer> findUnassignedCustomersByState(@Param("state") String state, Pageable pageable);

    /**
     * Update customer status and closure information
     * This matches the PATCH /api/mobile/customers/:id/status logic
     */
    @Modifying
    @Query("UPDATE Customer c SET " +
           "c.customerStatusString = :status, " +
           "c.isClosed = :isClosed, " +
           "c.lastStatusUpdated = :currentTime, " +
           "c.statusUpdatedBy = :userId, " +
           "c.notes = CASE WHEN :notes IS NOT NULL THEN :notes ELSE c.notes END, " +
           "c.reminderDate = :reminderDate, " +
           "c.updatedAt = :currentTime, " +
           "c.assignedTo = CASE WHEN :isClosed = true THEN NULL ELSE c.assignedTo END " +
           "WHERE c.id = :customerId AND c.assignedTo = :userId")
    int updateCustomerStatusAndUnassignIfClosed(
            @Param("customerId") Long customerId,
            @Param("status") String status,
            @Param("isClosed") Boolean isClosed,
            @Param("notes") String notes,
            @Param("reminderDate") LocalDateTime reminderDate,
            @Param("userId") Long userId,
            @Param("currentTime") LocalDateTime currentTime);

    /**
     * Assign multiple customers to a user
     */
    @Modifying
    @Query("UPDATE Customer c SET " +
           "c.assignedTo = :userId, " +
           "c.updatedAt = :assignedAt " +
           "WHERE c.id IN :customerIds AND c.assignedTo IS NULL")
    int assignCustomersToUser(
            @Param("customerIds") List<Long> customerIds,
            @Param("userId") Long userId,
            @Param("assignedAt") LocalDateTime assignedAt);

    /**
     * Get status breakdown for all customers updated by a user (for analytics)
     * This includes both currently assigned and previously submitted customers
     */
    @Query("SELECT c.customerStatusString as status, c.isClosed as closed, COUNT(c) as count " +
           "FROM Customer c WHERE c.statusUpdatedBy = :userId " +
           "AND c.customerStatusString IS NOT NULL " +
           "GROUP BY c.customerStatusString, c.isClosed " +
           "ORDER BY c.customerStatusString")
    List<Object[]> getStatusBreakdownForUser(@Param("userId") Long userId);
    
    /**
     * Find submitted/closed customers for a user
     * These are customers that were previously assigned to the user but are now closed
     * We need to track this through status_updated_by field since assignedTo is NULL for closed customers
     */
    @Query("SELECT c FROM Customer c WHERE c.statusUpdatedBy = :userId " +
           "AND c.isClosed = true " +
           "ORDER BY c.lastStatusUpdated DESC")
    Page<Customer> findSubmittedCustomersForUser(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Get status breakdown for submitted customers for a user
     */
    @Query("SELECT c.customerStatusString as status, c.isClosed as closed, COUNT(c) as count " +
           "FROM Customer c WHERE c.statusUpdatedBy = :userId " +
           "AND c.isClosed = true " +
           "GROUP BY c.customerStatusString, c.isClosed " +
           "ORDER BY c.customerStatusString")
    List<Object[]> getStatusBreakdownForSubmittedCustomers(@Param("userId") Long userId);

    /**
     * Find customers by mobile number for search
     */
    List<Customer> findByMobileNumberContaining(String mobileNumber);

    /**
     * Find customers by name (first name only since lastName removed from database)
     */
    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Customer> findByNameContaining(@Param("searchTerm") String searchTerm);

    /**
     * Find customers by state with pagination
     */
    Page<Customer> findByState(String state, Pageable pageable);
    
    /**
     * Find customers by state (without pagination)
     */
    List<Customer> findByState(String state);

    /**
     * Find distinct states from customers
     */
    @Query("SELECT DISTINCT c.state FROM Customer c WHERE c.state IS NOT NULL ORDER BY c.state")
    List<String> findDistinctStates();

    /**
     * Find customers by city and state
     */
    List<Customer> findByCityAndState(String city, String state);

    /**
     * Get customer statistics by state
     */
    @Query("SELECT c.state, COUNT(c) as totalCustomers, " +
           "SUM(CASE WHEN c.firstName IS NOT NULL THEN 1 ELSE 0 END) as customersWithNames " +
           "FROM Customer c WHERE c.state IS NOT NULL " +
           "GROUP BY c.state ORDER BY COUNT(c) DESC")
    List<Object[]> getCustomerStatisticsByState();

    /**
     * Get total customer count
     */
    @Query("SELECT COUNT(c) FROM Customer c")
    long getTotalCustomerCount();

    /**
     * Get total assigned customers count
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.assignedTo IS NOT NULL")
    long getTotalAssignedCustomersCount();

    /**
     * Get total unassigned customers count
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.assignedTo IS NULL AND (c.isClosed = false OR c.isClosed IS NULL)")
    long getTotalUnassignedCustomersCount();

    /**
     * Find customers created within date range
     */
    @Query("SELECT c FROM Customer c WHERE c.createdAt BETWEEN :fromDate AND :toDate ORDER BY c.createdAt DESC")
    Page<Customer> findCustomersCreatedBetween(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    /**
     * Find customers updated within date range
     */
    @Query("SELECT c FROM Customer c WHERE c.updatedAt BETWEEN :fromDate AND :toDate ORDER BY c.updatedAt DESC")
    Page<Customer> findCustomersUpdatedBetween(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    /**
     * Check if customer exists and is assigned to user (for security validation)
     */
    boolean existsByIdAndAssignedTo(Long customerId, Long userId);

    /**
     * Find customer by ID and assigned user (for security validation)
     */
    Optional<Customer> findByIdAndAssignedTo(Long customerId, Long userId);

    /**
     * Get daily customer creation statistics
     */
    @Query("SELECT CAST(c.createdAt AS DATE) as date, COUNT(c) as count " +
           "FROM Customer c WHERE c.createdAt >= :fromDate " +
           "GROUP BY CAST(c.createdAt AS DATE) ORDER BY CAST(c.createdAt AS DATE) DESC")
    List<Object[]> getDailyCustomerCreationStats(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Get daily customer update statistics (daily increments only)
     */
    @Query("SELECT CAST(c.lastStatusUpdated AS DATE) as date, COUNT(c) as count " +
           "FROM Customer c WHERE CAST(c.lastStatusUpdated AS DATE) BETWEEN CAST(:fromDate AS DATE) AND CAST(GETDATE() AS DATE) " +
           "GROUP BY CAST(c.lastStatusUpdated AS DATE) ORDER BY CAST(c.lastStatusUpdated AS DATE) DESC")
    List<Object[]> getDailyCustomerUpdateStats(@Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Get customer status breakdown for dashboard metrics
     */
    @Query("SELECT COALESCE(c.customerStatusString, 'not_started') as status, COUNT(c) as count " +
           "FROM Customer c " +
           "GROUP BY COALESCE(c.customerStatusString, 'not_started') " +
           "ORDER BY COUNT(c) DESC")
    List<Object[]> getCustomerStatusBreakdown();
    
    /**
     * Find customers by assigned user with pagination
     */
    Page<Customer> findByAssignedTo(Long assignedTo, Pageable pageable);
    
    /**
     * Find unassigned customers with pagination
     */
    Page<Customer> findByAssignedToIsNull(Pageable pageable);
    
    /**
     * Find customers by state and assigned user with pagination
     */
    Page<Customer> findByStateAndAssignedTo(String state, Long assignedTo, Pageable pageable);
    
    /**
     * Find customers by state and unassigned with pagination
     */
    Page<Customer> findByStateAndAssignedToIsNull(String state, Pageable pageable);
    
    // Analytics-related queries for mobile app
    
    /**
     * Count customers updated by a specific user since a certain date
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.statusUpdatedBy = :userId " +
           "AND c.lastStatusUpdated >= :since")
    long countCustomersUpdatedByUserSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    
    /**
     * Count closed customers for a specific user (completed work)
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.statusUpdatedBy = :userId " +
           "AND c.isClosed = true")
    long countClosedCustomersForUser(@Param("userId") Long userId);
    
    /**
     * Get daily customer update statistics for a specific user
     * Converts UTC timestamps to IST timezone for proper daily grouping
     */
    @Query("SELECT CAST(DATEADD(MINUTE, 330, c.lastStatusUpdated) AS DATE) as date, COUNT(c) as count " +
           "FROM Customer c WHERE c.statusUpdatedBy = :userId " +
           "AND c.lastStatusUpdated >= :fromDate " +
           "GROUP BY CAST(DATEADD(MINUTE, 330, c.lastStatusUpdated) AS DATE) " +
           "ORDER BY CAST(DATEADD(MINUTE, 330, c.lastStatusUpdated) AS DATE) DESC")
    List<Object[]> getDailyCustomerUpdateStatsForUser(@Param("userId") Long userId, @Param("fromDate") LocalDateTime fromDate);
}
