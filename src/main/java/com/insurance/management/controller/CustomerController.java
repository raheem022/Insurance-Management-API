package com.insurance.management.controller;

import com.insurance.management.entity.Customer;
import com.insurance.management.service.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer Controller
 * Provides customer management endpoints for admin interface
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class CustomerController {
    
    private final CustomerService customerService;
    
    /**
     * GET /api/customers
     * Get all customers with pagination
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCustomers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "id DESC") String sort,
            @RequestParam(required = false) String state,
            @RequestParam(name = "assigned_to", required = false) String assignedTo) {
        
        log.info("📋 Customer list request - Page: {}, Size: {}, State: {}, AssignedTo: {}", page, size, state, assignedTo);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Parse sort parameter - default to 'id DESC' to match Node.js behavior
            Sort sortObj = parseSortParameter(sort);
            Pageable pageable = PageRequest.of(page - 1, size, sortObj);
            
            // Get customers with filtering like Node.js API
            Page<Customer> customersPage = customerService.getCustomersWithFilters(state, assignedTo, pageable);
            
            // Return response with data and pagination metadata for frontend
            response.put("success", true);
            response.put("data", customersPage.getContent());
            response.put("total", customersPage.getTotalElements());
            response.put("totalPages", customersPage.getTotalPages());
            response.put("currentPage", page);
            response.put("pageSize", size);
            
            log.info("✅ Found {} customers on page {} (total: {})", customersPage.getContent().size(), page, customersPage.getTotalElements());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching customers", e);
            response.put("success", false);
            response.put("error", "Failed to fetch customers");
            return ResponseEntity.status(500).body(response);
        }
    }
    
    private Sort parseSortParameter(String sort) {
        try {
            String[] parts = sort.split(" ");
            String field = convertToCamelCase(parts[0]);
            String direction = parts.length > 1 ? parts[1] : "DESC";
            
            return Sort.by(Sort.Direction.fromString(direction), field);
        } catch (Exception e) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
    }
    
    private String convertToCamelCase(String snakeCase) {
        switch (snakeCase.toLowerCase()) {
            case "created_at":
                return "createdAt";
            case "updated_at":
                return "updatedAt";
            case "first_name":
                return "firstName";
            case "mobile_number":
                return "mobileNumber";
            case "id":
                return "id";  // Keep 'id' as is - it's already correct
            default:
                return snakeCase;
        }
    }
}
