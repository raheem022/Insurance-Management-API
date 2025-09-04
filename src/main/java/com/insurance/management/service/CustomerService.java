package com.insurance.management.service;

import com.insurance.management.entity.Customer;
import com.insurance.management.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Customer Service - Business logic layer for Customer operations
 * Based on the existing Node.js server implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Get customers allocated to a user (OPEN only by default)
     * Matches: GET /api/mobile/customers/allocated from Node.js
     */
    public Page<Customer> getAllocatedCustomers(Long userId, int page, int size, boolean includeClosed) {
        Pageable pageable = PageRequest.of(page - 1, size); // Convert to 0-based indexing
        
        if (includeClosed) {
            log.info("📋 Including ALL customers (open and closed) for user: {}", userId);
            return customerRepository.findAllCustomersAssignedToUser(userId, pageable);
        } else {
            log.info("📂 Filtering to show OPEN customers only (excluding closed) for user: {}", userId);
            LocalDateTime currentTime = LocalDateTime.now();
            return customerRepository.findOpenCustomersAssignedToUser(userId, currentTime, pageable);
        }
    }

    /**
     * Get count of allocated customers
     */
    public long getAllocatedCustomersCount(Long userId, boolean includeClosed) {
        if (includeClosed) {
            return customerRepository.countByAssignedTo(userId);
        } else {
            LocalDateTime currentTime = LocalDateTime.now();
            return customerRepository.countOpenCustomersAssignedToUser(userId, currentTime);
        }
    }

    /**
     * Get status breakdown for user's assigned customers
     * Matches the status breakdown from Node.js implementation
     */
    public List<Object[]> getStatusBreakdownForUser(Long userId) {
        return customerRepository.getStatusBreakdownForUser(userId);
    }
    
    /**
     * Get submitted/closed customers for a user
     * These are customers that were previously assigned to the user but are now closed
     */
    public Page<Customer> getSubmittedCustomers(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        log.info("📋 Fetching submitted customers for user: {}", userId);
        return customerRepository.findSubmittedCustomersForUser(userId, pageable);
    }
    
    /**
     * Get status breakdown for submitted customers for a user
     */
    public List<Object[]> getStatusBreakdownForSubmittedCustomers(Long userId) {
        return customerRepository.getStatusBreakdownForSubmittedCustomers(userId);
    }

    /**
     * Get customers due for follow-up
     * Matches: GET /api/mobile/customers/follow-up from Node.js
     */
    public Page<Customer> getFollowUpCustomers(Long userId, int page, int size, int daysAhead) {
        Pageable pageable = PageRequest.of(page - 1, size);
        LocalDateTime futureDate = LocalDateTime.now().plusDays(daysAhead);
        
        log.info("📅 Fetching follow-up customers for user: {} due within {} days", userId, daysAhead);
        return customerRepository.findFollowUpCustomersForUser(userId, futureDate, pageable);
    }

    /**
     * Count follow-up customers
     */
    public long getFollowUpCustomersCount(Long userId, int daysAhead) {
        LocalDateTime futureDate = LocalDateTime.now().plusDays(daysAhead);
        return customerRepository.countFollowUpCustomersForUser(userId, futureDate);
    }

    /**
     * Update customer status with unassignment logic
     * Matches: PATCH /api/mobile/customers/:id/status from Node.js
     */
    public boolean updateCustomerStatus(Long customerId, Long userId, Customer.CustomerStatus status, 
                                      String notes, LocalDateTime reminderDate) {
        
        // Validate the 5 allowed statuses (same as Node.js validation)
        if (!isValidStatus(status)) {
            log.error("❌ Invalid status: {}. Must be one of the 5 allowed statuses", status);
            return false;
        }

        // Determine if customer should be closed based on status (same logic as Node.js)
        boolean isClosed = isClosedStatus(status);
        
        // Validate reminder_date for follow_up status (same validation as Node.js)
        if (status == Customer.CustomerStatus.FOLLOW_UP && reminderDate != null) {
            if (reminderDate.isBefore(LocalDateTime.now()) || reminderDate.isEqual(LocalDateTime.now())) {
                log.error("❌ Reminder date must be a valid future date for follow-up status");
                return false;
            }
        }

        log.info("🔄 Updating customer {} status to: {} (closed: {}) by user: {}", 
                customerId, status.getValue(), isClosed, userId);

        LocalDateTime currentTime = LocalDateTime.now();
        int updatedRows = customerRepository.updateCustomerStatusAndUnassignIfClosed(
                customerId, status.getValue(), isClosed, notes, reminderDate, userId, currentTime
        );

        boolean success = updatedRows > 0;
        
        if (success) {
            if (isClosed) {
                log.info("🔒 Customer {} marked as CLOSED with status: {} and UNASSIGNED from user", 
                        customerId, status.getValue());
            } else {
                log.info("📂 Customer {} remains OPEN with status: {} and stays assigned", 
                        customerId, status.getValue());
            }
        } else {
            log.error("❌ Customer not found or not assigned to user: customerId={}, userId={}", 
                    customerId, userId);
        }

        return success;
    }

    /**
     * Submit individual customer with immediate unassignment
     * This is the NEW endpoint that the mobile app expects: POST /api/mobile/customers/submit-individual
     */
    public boolean submitIndividualCustomerWithUnassignment(Long customerId, Long userId, 
                                                          Customer.CustomerStatus status, 
                                                          String notes, LocalDateTime reminderDate) {
        
        log.info("🔄 Submitting individual customer with immediate unassignment - Customer ID: {}", customerId);
        
        // Always unassign after individual submission regardless of status
        // This is different from the regular status update - it always moves customer away from user
        boolean isClosed = true; // Force closure to unassign
        LocalDateTime currentTime = LocalDateTime.now();
        
        int updatedRows = customerRepository.updateCustomerStatusAndUnassignIfClosed(
                customerId, status.getValue(), isClosed, notes, reminderDate, userId, currentTime
        );

        boolean success = updatedRows > 0;
        
        if (success) {
            log.info("✅ Individual customer submission successful - Customer {} updated to '{}' and moved to submissions", 
                    customerId, status.getValue());
        } else {
            log.error("❌ Individual submission failed - Customer {} not found or not assigned to user {}", 
                    customerId, userId);
        }

        return success;
    }

    /**
     * Get customer by ID if assigned to user (for security validation)
     */
    public Optional<Customer> getCustomerIfAssignedToUser(Long customerId, Long userId) {
        return customerRepository.findByIdAndAssignedTo(customerId, userId);
    }

    /**
     * Check if customer exists and is assigned to user
     */
    public boolean isCustomerAssignedToUser(Long customerId, Long userId) {
        return customerRepository.existsByIdAndAssignedTo(customerId, userId);
    }

    /**
     * Assign customers to a user
     */
    public int assignCustomersToUser(List<Long> customerIds, Long userId, Long assignedBy) {
        LocalDateTime assignedAt = LocalDateTime.now();
        return customerRepository.assignCustomersToUser(customerIds, userId, assignedAt);
    }

    /**
     * Get all customers with pagination and sorting
     * Matches: GET /api/customers from frontend
     */
    public Page<Customer> getAllCustomers(Pageable pageable) {
        log.info("📋 Fetching all customers with pagination - page: {}, size: {}, sort: {}", 
                 pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        return customerRepository.findAll(pageable);
    }

    /**
     * Get customers with state filter (matches Node.js API filtering)
     * Supports: state filter, assigned_to filter
     */
    public Page<Customer> getCustomersWithFilters(String state, String assignedTo, Pageable pageable) {
        log.info("📋 Fetching customers with filters - State: {}, AssignedTo: {}, page: {}, size: {}", 
                 state, assignedTo, pageable.getPageNumber(), pageable.getPageSize());
        
        if (state != null && !state.trim().isEmpty()) {
            if (assignedTo != null) {
                if ("null".equals(assignedTo) || assignedTo.trim().isEmpty()) {
                    // Unassigned customers with state filter
                    return customerRepository.findByStateAndAssignedToIsNull(state, pageable);
                } else {
                    // Assigned customers with state filter
                    Long userId = Long.parseLong(assignedTo);
                    return customerRepository.findByStateAndAssignedTo(state, userId, pageable);
                }
            } else {
                // Only state filter
                return customerRepository.findByState(state, pageable);
            }
        } else if (assignedTo != null) {
            if ("null".equals(assignedTo) || assignedTo.trim().isEmpty()) {
                // Only unassigned filter
                return customerRepository.findByAssignedToIsNull(pageable);
            } else {
                // Only assigned filter
                Long userId = Long.parseLong(assignedTo);
                return customerRepository.findByAssignedTo(userId, pageable);
            }
        } else {
            // No filters - return all customers
            return customerRepository.findAll(pageable);
        }
    }

    /**
     * Get unassigned customers available for assignment
     */
    public Page<Customer> getUnassignedCustomers(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return customerRepository.findUnassignedCustomers(pageable);
    }

    /**
     * Get unassigned customers by state
     */
    public Page<Customer> getUnassignedCustomersByState(String state, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return customerRepository.findUnassignedCustomersByState(state, pageable);
    }

    /**
     * Search customers by various criteria
     */
    public List<Customer> searchCustomers(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of();
        }

        String trimmedTerm = searchTerm.trim();
        
        // Try mobile number search first
        if (trimmedTerm.matches("\\d+")) {
            List<Customer> byMobile = customerRepository.findByMobileNumberContaining(trimmedTerm);
            if (!byMobile.isEmpty()) {
                return byMobile;
            }
        }
        
        // Try name search
        return customerRepository.findByNameContaining(trimmedTerm);
    }

    /**
     * Get customer statistics
     */
    public List<Object[]> getCustomerStatisticsByState() {
        return customerRepository.getCustomerStatisticsByState();
    }

    /**
     * Get total customer counts
     */
    public long getTotalCustomerCount() {
        return customerRepository.getTotalCustomerCount();
    }

    public long getTotalAssignedCustomersCount() {
        return customerRepository.getTotalAssignedCustomersCount();
    }

    public long getTotalUnassignedCustomersCount() {
        return customerRepository.getTotalUnassignedCustomersCount();
    }

    /**
     * Get customers within date range
     */
    public Page<Customer> getCustomersCreatedBetween(LocalDateTime fromDate, LocalDateTime toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return customerRepository.findCustomersCreatedBetween(fromDate, toDate, pageable);
    }

    public Page<Customer> getCustomersUpdatedBetween(LocalDateTime fromDate, LocalDateTime toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return customerRepository.findCustomersUpdatedBetween(fromDate, toDate, pageable);
    }

    /**
     * Validate status - matches Node.js validation
     */
    private boolean isValidStatus(Customer.CustomerStatus status) {
        // The 5 allowed statuses from Node.js implementation
        return status == Customer.CustomerStatus.ACTIVE ||
               status == Customer.CustomerStatus.RENEWED ||
               status == Customer.CustomerStatus.NOT_INTERESTED ||
               status == Customer.CustomerStatus.NOT_REACHABLE ||
               status == Customer.CustomerStatus.FOLLOW_UP;
    }

    /**
     * Check if status represents a closed customer - matches Node.js logic
     */
    private boolean isClosedStatus(Customer.CustomerStatus status) {
        // Closed statuses from Node.js: ['active', 'renewed', 'not_interested']
        return status == Customer.CustomerStatus.ACTIVE ||
               status == Customer.CustomerStatus.RENEWED ||
               status == Customer.CustomerStatus.NOT_INTERESTED;
    }

    /**
     * Get daily statistics
     */
    public List<Object[]> getDailyCustomerCreationStats(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        return customerRepository.getDailyCustomerCreationStats(fromDate);
    }

    public List<Object[]> getDailyCustomerUpdateStats(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        return customerRepository.getDailyCustomerUpdateStats(fromDate);
    }
}
