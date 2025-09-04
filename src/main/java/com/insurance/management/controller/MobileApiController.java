package com.insurance.management.controller;

import com.insurance.management.dto.CustomerDTO;
import com.insurance.management.entity.Customer;
import com.insurance.management.entity.User;
import com.insurance.management.service.CustomerService;
import com.insurance.management.service.UserService;
import com.insurance.management.util.MobileTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Mobile API Controller - REST endpoints for mobile application
 * Based on the existing Node.js mobile API endpoints
 */
@RestController
@RequestMapping("/mobile")
@RequiredArgsConstructor
@Slf4j
@Validated
public class MobileApiController {

    private final CustomerService customerService;
    private final UserService userService;
    private final MobileTokenUtil tokenUtil;

    /**
     * GET /api/mobile/customers/allocated
     * Get OPEN customers allocated to the authenticated mobile user
     * Matches the Node.js implementation exactly
     */
    @GetMapping("/customers/allocated")
    public ResponseEntity<CustomerDTO.PaginatedResponse> getAllocatedCustomers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(name = "include_closed", defaultValue = "false") boolean includeClosed,
            HttpServletRequest request) {
        
        log.info("📱 Mobile allocated customers request received");
        
        // Extract and validate token
        User user = tokenUtil.validateTokenAndGetUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid or expired authentication token"));
        }
        
        log.info("📋 Fetching {} customers for user: {} (ID: {})", 
                includeClosed ? "ALL" : "OPEN", user.getUsername(), user.getId());
        
        // Get customers with pagination
        Page<Customer> customersPage = customerService.getAllocatedCustomers(
                user.getId(), page, size, includeClosed);
        
        // Get status breakdown
        List<Object[]> statusBreakdownData = customerService.getStatusBreakdownForUser(user.getId());
        
        // Convert to response format
        CustomerDTO.PaginatedResponse response = buildPaginatedResponse(
                customersPage, user, includeClosed, statusBreakdownData);
        
        log.info("✅ Found {} {} allocated customers", 
                customersPage.getContent().size(), includeClosed ? "total" : "OPEN");
        
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/mobile/customers/{id}/status
     * Update customer status using the new 5-status system with closure logic
     * Matches the Node.js implementation exactly
     */
    @PatchMapping("/customers/{id}/status")
    public ResponseEntity<CustomerDTO.StatusUpdateResponse> updateCustomerStatus(
            @PathVariable Long id,
            @Valid @RequestBody CustomerDTO.StatusUpdateRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("📱 Mobile customer status update request received");
        
        // Extract and validate token
        User user = tokenUtil.validateTokenAndGetUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createStatusUpdateErrorResponse("Invalid or expired authentication token"));
        }
        
        // Convert string status to enum
        Customer.CustomerStatus status;
        try {
            status = Customer.CustomerStatus.fromValue(request.getStatus());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(createStatusUpdateErrorResponse("Invalid status: " + request.getStatus()));
        }
        
        // Update customer status
        boolean success = customerService.updateCustomerStatus(
                id, user.getId(), status, request.getNotes(), request.getReminderDate());
        
        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createStatusUpdateErrorResponse("Customer not found or not assigned to this user"));
        }
        
        // Build response
        CustomerDTO.StatusUpdateResponse response = buildStatusUpdateResponse(
                id, status, request.getReminderDate(), user.getUsername());
        
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/mobile/customers/submit-individual
     * NEW ENDPOINT: Submit individual customer with immediate unassignment
     * This is the endpoint that the mobile app expects but was missing in Node.js
     */
    @PostMapping("/customers/submit-individual")
    public ResponseEntity<CustomerDTO.IndividualSubmissionResponse> submitIndividualCustomer(
            @Valid @RequestBody CustomerDTO.IndividualSubmissionRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("🔄 Mobile individual customer submission request received");
        
        // Extract and validate token
        User user = tokenUtil.validateTokenAndGetUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createIndividualSubmissionErrorResponse("Invalid or expired authentication token"));
        }
        
        // Validate that the request userId matches the authenticated user
        if (!user.getId().equals(request.getUserId())) {
            log.warn("⚠️ User ID mismatch: authenticated user {} vs request user {}", 
                    user.getId(), request.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(createIndividualSubmissionErrorResponse("User ID mismatch"));
        }
        
        // Convert string status to enum
        Customer.CustomerStatus status;
        try {
            status = Customer.CustomerStatus.fromValue(request.getStatus());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(createIndividualSubmissionErrorResponse("Invalid status: " + request.getStatus()));
        }
        
        log.info("🔄 Submitting individual customer with immediate unassignment - Customer ID: {}", 
                request.getCustomerId());
        
        // Submit individual customer with unassignment (always unassigns regardless of status)
        boolean success = customerService.submitIndividualCustomerWithUnassignment(
                request.getCustomerId(), user.getId(), status, request.getNotes(), request.getFollowUpDate());
        
        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createIndividualSubmissionErrorResponse("Customer not found or not assigned to this user"));
        }
        
        // Build success response
        CustomerDTO.IndividualSubmissionResponse response = buildIndividualSubmissionResponse(
                request.getCustomerId(), status.getValue(), user.getUsername());
        
        log.info("✅ Individual customer submission successful - Customer {} moved to submissions", 
                request.getCustomerId());
        
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/mobile/customers/submissions
     * Get submitted/closed customers for the authenticated mobile user
     * Shows customers that have been completed and moved to submissions
     */
    @GetMapping("/customers/submissions")
    public ResponseEntity<CustomerDTO.PaginatedResponse> getSubmittedCustomers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        
        log.info("📱 Mobile submissions request received");
        
        // Extract and validate token
        User user = tokenUtil.validateTokenAndGetUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid or expired authentication token"));
        }
        
        log.info("📋 Fetching submitted customers for user: {} (ID: {})", 
                user.getUsername(), user.getId());
        
        // Get closed/submitted customers for this user
        Page<Customer> customersPage = customerService.getSubmittedCustomers(
                user.getId(), page, size);
        
        // Get status breakdown for submitted customers
        List<Object[]> statusBreakdownData = customerService.getStatusBreakdownForSubmittedCustomers(user.getId());
        
        // Convert to response format
        CustomerDTO.PaginatedResponse response = buildSubmissionsResponse(
                customersPage, user, statusBreakdownData);
        
        log.info("✅ Found {} submitted customers", customersPage.getContent().size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/mobile/customers/follow-up
     * Get customers due for follow-up based on reminder dates
     * Matches the Node.js implementation
     */
    @GetMapping("/customers/follow-up")
    public ResponseEntity<CustomerDTO.PaginatedResponse> getFollowUpCustomers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(name = "days_ahead", defaultValue = "7") int daysAhead,
            HttpServletRequest request) {
        
        log.info("📱 Mobile follow-up customers request received");
        
        // Extract and validate token
        User user = tokenUtil.validateTokenAndGetUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid or expired authentication token"));
        }
        
        log.info("📅 Fetching follow-up customers for user: {} (ID: {}) due within {} days", 
                user.getUsername(), user.getId(), daysAhead);
        
        // Get follow-up customers
        Page<Customer> customersPage = customerService.getFollowUpCustomers(
                user.getId(), page, size, daysAhead);
        
        // Build response (simpler for follow-up customers)
        CustomerDTO.PaginatedResponse response = buildFollowUpResponse(
                customersPage, user, daysAhead);
        
        log.info("✅ Found {} follow-up customers due within {} days", 
                customersPage.getContent().size(), daysAhead);
        
        return ResponseEntity.ok(response);
    }

    // Helper methods for building responses

    private CustomerDTO.PaginatedResponse buildPaginatedResponse(Page<Customer> customersPage, 
                                                               User user, boolean includeClosed, 
                                                               List<Object[]> statusBreakdownData) {
        CustomerDTO.PaginatedResponse response = new CustomerDTO.PaginatedResponse();
        CustomerDTO.PaginatedResponse.CustomerData data = new CustomerDTO.PaginatedResponse.CustomerData();
        
        // Convert customers to response format
        List<CustomerDTO.Response> customers = customersPage.getContent().stream()
                .map(this::convertCustomerToResponse)
                .collect(Collectors.toList());
        data.setCustomers(customers);
        
        // Pagination info
        CustomerDTO.PaginatedResponse.Pagination pagination = new CustomerDTO.PaginatedResponse.Pagination();
        pagination.setCurrentPage(customersPage.getNumber() + 1); // Convert back to 1-based
        pagination.setPageSize(customersPage.getSize());
        pagination.setTotalCount(customersPage.getTotalElements());
        pagination.setTotalPages(customersPage.getTotalPages());
        data.setPagination(pagination);
        
        // User info
        CustomerDTO.PaginatedResponse.UserInfo userInfo = new CustomerDTO.PaginatedResponse.UserInfo();
        userInfo.setUsername(user.getUsername());
        userInfo.setUserId(user.getId());
        userInfo.setState(user.getLocationState());
        data.setUserInfo(userInfo);
        
        // Filter info
        CustomerDTO.PaginatedResponse.FilterInfo filterInfo = new CustomerDTO.PaginatedResponse.FilterInfo();
        filterInfo.setIncludeClosed(includeClosed);
        filterInfo.setShowing(includeClosed ? "All customers" : "Open customers only");
        data.setFilterInfo(filterInfo);
        
        // Status breakdown
        List<CustomerDTO.PaginatedResponse.StatusBreakdown> statusBreakdown = statusBreakdownData.stream()
                .map(row -> {
                    CustomerDTO.PaginatedResponse.StatusBreakdown breakdown = new CustomerDTO.PaginatedResponse.StatusBreakdown();
                    breakdown.setCustomerStatus((String) row[0]);
                    breakdown.setIsClosed((Boolean) row[1]);
                    breakdown.setCount(((Number) row[2]).longValue());
                    return breakdown;
                })
                .collect(Collectors.toList());
        data.setStatusBreakdown(statusBreakdown);
        
        // System info
        CustomerDTO.PaginatedResponse.SystemInfo systemInfo = new CustomerDTO.PaginatedResponse.SystemInfo();
        systemInfo.setStatusRules(new CustomerDTO.PaginatedResponse.SystemInfo.StatusRules());
        data.setSystemInfo(systemInfo);
        
        response.setData(data);
        return response;
    }

    private CustomerDTO.PaginatedResponse buildFollowUpResponse(Page<Customer> customersPage, 
                                                              User user, int daysAhead) {
        CustomerDTO.PaginatedResponse response = new CustomerDTO.PaginatedResponse();
        CustomerDTO.PaginatedResponse.CustomerData data = new CustomerDTO.PaginatedResponse.CustomerData();
        
        // Convert customers
        List<CustomerDTO.Response> customers = customersPage.getContent().stream()
                .map(this::convertCustomerToResponse)
                .collect(Collectors.toList());
        data.setCustomers(customers);
        
        // Pagination
        CustomerDTO.PaginatedResponse.Pagination pagination = new CustomerDTO.PaginatedResponse.Pagination();
        pagination.setCurrentPage(customersPage.getNumber() + 1);
        pagination.setPageSize(customersPage.getSize());
        pagination.setTotalCount(customersPage.getTotalElements());
        pagination.setTotalPages(customersPage.getTotalPages());
        data.setPagination(pagination);
        
        // User info
        CustomerDTO.PaginatedResponse.UserInfo userInfo = new CustomerDTO.PaginatedResponse.UserInfo();
        userInfo.setUsername(user.getUsername());
        userInfo.setUserId(user.getId());
        userInfo.setState(user.getLocationState());
        data.setUserInfo(userInfo);
        
        // Filter info for follow-up
        CustomerDTO.PaginatedResponse.FilterInfo filterInfo = new CustomerDTO.PaginatedResponse.FilterInfo();
        filterInfo.setIncludeClosed(false);
        filterInfo.setShowing("Follow-up customers due within " + daysAhead + " days");
        data.setFilterInfo(filterInfo);
        
        response.setData(data);
        return response;
    }
    
    private CustomerDTO.PaginatedResponse buildSubmissionsResponse(Page<Customer> customersPage, 
                                                                 User user, 
                                                                 List<Object[]> statusBreakdownData) {
        CustomerDTO.PaginatedResponse response = new CustomerDTO.PaginatedResponse();
        CustomerDTO.PaginatedResponse.CustomerData data = new CustomerDTO.PaginatedResponse.CustomerData();
        
        // Convert customers to response format
        List<CustomerDTO.Response> customers = customersPage.getContent().stream()
                .map(this::convertCustomerToResponse)
                .collect(Collectors.toList());
        data.setCustomers(customers);
        
        // Pagination info
        CustomerDTO.PaginatedResponse.Pagination pagination = new CustomerDTO.PaginatedResponse.Pagination();
        pagination.setCurrentPage(customersPage.getNumber() + 1); // Convert back to 1-based
        pagination.setPageSize(customersPage.getSize());
        pagination.setTotalCount(customersPage.getTotalElements());
        pagination.setTotalPages(customersPage.getTotalPages());
        data.setPagination(pagination);
        
        // User info
        CustomerDTO.PaginatedResponse.UserInfo userInfo = new CustomerDTO.PaginatedResponse.UserInfo();
        userInfo.setUsername(user.getUsername());
        userInfo.setUserId(user.getId());
        userInfo.setState(user.getLocationState());
        data.setUserInfo(userInfo);
        
        // Filter info for submissions
        CustomerDTO.PaginatedResponse.FilterInfo filterInfo = new CustomerDTO.PaginatedResponse.FilterInfo();
        filterInfo.setIncludeClosed(true);
        filterInfo.setShowing("My submitted customers");
        data.setFilterInfo(filterInfo);
        
        // Status breakdown for submitted customers
        List<CustomerDTO.PaginatedResponse.StatusBreakdown> statusBreakdown = statusBreakdownData.stream()
                .map(row -> {
                    CustomerDTO.PaginatedResponse.StatusBreakdown breakdown = new CustomerDTO.PaginatedResponse.StatusBreakdown();
                    breakdown.setCustomerStatus((String) row[0]);
                    breakdown.setIsClosed((Boolean) row[1]);
                    breakdown.setCount(((Number) row[2]).longValue());
                    return breakdown;
                })
                .collect(Collectors.toList());
        data.setStatusBreakdown(statusBreakdown);
        
        // System info
        CustomerDTO.PaginatedResponse.SystemInfo systemInfo = new CustomerDTO.PaginatedResponse.SystemInfo();
        systemInfo.setStatusRules(new CustomerDTO.PaginatedResponse.SystemInfo.StatusRules());
        data.setSystemInfo(systemInfo);
        
        response.setData(data);
        return response;
    }

    private CustomerDTO.Response convertCustomerToResponse(Customer customer) {
        CustomerDTO.Response response = new CustomerDTO.Response();
        response.setId(customer.getId());
        response.setFirstName(customer.getFirstName());
        response.setLastName(""); // lastname field removed from database
        response.setMobileNumber(customer.getMobileNumber());
        response.setCustomerEmailAddress(customer.getCustomerEmailAddress());
        response.setCity(customer.getCity());
        response.setState(customer.getState());
        response.setPinCode(customer.getPinCode());
        response.setAddressLine1(customer.getAddressLine1());
        response.setRegistrationNumber(customer.getRegistrationNumber());
        response.setVehicleMake(customer.getVehicleMake());
        response.setVehicleModel(customer.getVehicleModel());
        response.setModelVariant(customer.getModelVariant());
        response.setColor(customer.getColor());
        response.setRegistrationDate(customer.getRegistrationDate());
        response.setPreviousInsuranceName(customer.getPreviousInsuranceName());
        response.setPreviousInsuranceNumber(customer.getPreviousInsuranceNumber());
        response.setCustomerStatus(customer.getCustomerStatus() != null ? customer.getCustomerStatus().getValue() : null);
        response.setIsClosed(customer.getIsClosed());
        response.setLastStatusUpdated(customer.getLastStatusUpdated());
        response.setReminderDate(customer.getReminderDate());
        response.setAssignedTo(customer.getAssignedTo());
        response.setAssignedAt(null); // assignedAt field removed from database
        response.setNotes(customer.getNotes());
        response.setLastContactDate(null); // lastContactDate field removed from database
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());
        response.setStatusUpdatedBy(customer.getStatusUpdatedBy());
        
        return response;
    }

    private CustomerDTO.StatusUpdateResponse buildStatusUpdateResponse(Long customerId, 
                                                                     Customer.CustomerStatus status,
                                                                     LocalDateTime reminderDate, 
                                                                     String updatedBy) {
        CustomerDTO.StatusUpdateResponse response = new CustomerDTO.StatusUpdateResponse();
        CustomerDTO.StatusUpdateResponse.StatusUpdateData data = new CustomerDTO.StatusUpdateResponse.StatusUpdateData();
        
        data.setCustomerId(customerId);
        data.setStatus(status.getValue());
        data.setIsClosed(status.isClosed());
        data.setReminderDate(reminderDate);
        data.setUpdatedBy(updatedBy);
        
        // Closure info
        CustomerDTO.StatusUpdateResponse.StatusUpdateData.ClosureInfo closureInfo = 
                new CustomerDTO.StatusUpdateResponse.StatusUpdateData.ClosureInfo();
        closureInfo.setActionTaken(status.isClosed() ? 
                "Customer completed and unassigned from user" : 
                "Customer remains assigned to user");
        data.setClosureInfo(closureInfo);
        
        response.setData(data);
        return response;
    }

    private CustomerDTO.IndividualSubmissionResponse buildIndividualSubmissionResponse(Long customerId, 
                                                                                     String status, 
                                                                                     String updatedBy) {
        CustomerDTO.IndividualSubmissionResponse response = new CustomerDTO.IndividualSubmissionResponse();
        response.setMessage("Customer status updated to '" + status + "' and moved to submissions!");
        
        CustomerDTO.IndividualSubmissionResponse.IndividualSubmissionData data = 
                new CustomerDTO.IndividualSubmissionResponse.IndividualSubmissionData();
        data.setCustomerId(customerId);
        data.setStatus(status);
        data.setUpdatedBy(updatedBy);
        
        response.setData(data);
        return response;
    }

    // Error response helpers
    private CustomerDTO.PaginatedResponse createErrorResponse(String error) {
        CustomerDTO.PaginatedResponse response = new CustomerDTO.PaginatedResponse();
        response.setSuccess(false);
        // Note: In a real implementation, you'd handle this properly
        return response;
    }

    private CustomerDTO.StatusUpdateResponse createStatusUpdateErrorResponse(String error) {
        CustomerDTO.StatusUpdateResponse response = new CustomerDTO.StatusUpdateResponse();
        response.setSuccess(false);
        return response;
    }

    private CustomerDTO.IndividualSubmissionResponse createIndividualSubmissionErrorResponse(String error) {
        CustomerDTO.IndividualSubmissionResponse response = new CustomerDTO.IndividualSubmissionResponse();
        response.setSuccess(false);
        response.setMessage("Failed to update customer: " + error);
        return response;
    }
}
