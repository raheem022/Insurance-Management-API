package com.insurance.management.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Customer Entity - Maps to the main customers table in DataSync database
 * This entity follows the existing database schema from Node.js implementation
 */
@Entity
@Table(name = "customers")
@Data
@EqualsAndHashCode(callSuper = false)
@EntityListeners(AuditingEntityListener.class)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Basic customer information
    @Column(name = "firstname")
    @JsonProperty("firstname")
    private String firstName;

    @Column(name = "mobilenumber")
    @JsonProperty("mobilenumber")
    private String mobileNumber;

    @Column(name = "customeremailaddress")
    @JsonProperty("customeremailaddress")
    private String customerEmailAddress;

    // Location information
    @Column(name = "city")
    @JsonProperty("city")
    private String city;

    @Column(name = "state")
    @JsonProperty("state")
    private String state;

    @Column(name = "pincode")
    @JsonProperty("pincode")
    private String pinCode;

    @Column(name = "addressline1")
    @JsonProperty("addressline1")
    private String addressLine1;

    @Column(name = "addressline2")
    @JsonProperty("addressline2")
    private String addressLine2;

    @Column(name = "address")
    @JsonProperty("address")
    private String address;

    @Column(name = "contactperson")
    @JsonProperty("contactperson")
    private String contactPerson;

    @Column(name = "gender")
    @JsonProperty("gender")
    private String gender;

    @Column(name = "relativename")
    @JsonProperty("relativename")
    private String relativeName;

    @Column(name = "bookingdate")
    @JsonProperty("bookingdate")
    private LocalDate bookingDate;

    @Column(name = "invoicedate")
    @JsonProperty("invoicedate")
    private LocalDate invoiceDate;

    // Vehicle information
    @Column(name = "registrationnum")
    @JsonProperty("registrationnum")
    private String registrationNumber;

    @Column(name = "vehiclemake")
    @JsonProperty("vehiclemake")
    private String vehicleMake;

    @Column(name = "vehmodel")
    @JsonProperty("vehmodel")
    private String vehicleModel;

    @Column(name = "modelvariant")
    @JsonProperty("modelvariant")
    private String modelVariant;

    @Column(name = "color")
    @JsonProperty("color")
    private String color;

    @Column(name = "registrationdate")
    @JsonProperty("registrationdate")
    private LocalDate registrationDate;

    @Column(name = "chassisnum")
    @JsonProperty("chassisnum")
    private String chassisNumber;

    @Column(name = "enginenum")
    @JsonProperty("enginenum")
    private String engineNumber;

    @Column(name = "cc")
    @JsonProperty("cc")
    private Integer cc;

    @Column(name = "manufacture")
    @JsonProperty("manufacture")
    private String manufacture;

    @Column(name = "typeofbody")
    @JsonProperty("typeofbody")
    private String typeOfBody;

    @Column(name = "financier")
    @JsonProperty("financier")
    private String financier;

    @Column(name = "hypothecation")
    @JsonProperty("hypothecation")
    private String hypothecation;

    // Insurance information
    @Column(name = "previousinsname")
    @JsonProperty("previousinsname")
    private String previousInsuranceName;

    @Column(name = "previnsno")
    @JsonProperty("previnsno")
    private String previousInsuranceNumber;

    // Status management (New 5-status system)
    @Column(name = "customer_status")
    @JsonProperty("customer_status")
    private String customerStatusString;
    
    @Transient
    @JsonIgnore
    private CustomerStatus customerStatus = CustomerStatus.NOT_STARTED;
    
    // Custom getter that maps string to enum
    @JsonIgnore
    public CustomerStatus getCustomerStatus() {
        if (customerStatusString == null) return CustomerStatus.NOT_STARTED;
        return CustomerStatus.fromString(customerStatusString);
    }
    
    // Custom setter that maps enum to string  
    @JsonIgnore
    public void setCustomerStatus(CustomerStatus status) {
        this.customerStatus = status;
        this.customerStatusString = status != null ? status.getValue() : null;
    }

    @Column(name = "is_closed")
    @JsonProperty("is_closed")
    private Boolean isClosed = false;

    @Column(name = "last_status_updated")
    @JsonProperty("last_status_updated")
    private LocalDateTime lastStatusUpdated;

    @Column(name = "status_updated_by")
    @JsonProperty("status_updated_by")
    private Long statusUpdatedBy;

    @Column(name = "reminder_date")
    @JsonProperty("reminder_date")
    private LocalDateTime reminderDate;

    // Assignment management
    @Column(name = "assigned_to")
    @JsonProperty("assigned_to")
    private Long assignedTo;

    // Legacy status fields (for backward compatibility)
    @Column(name = "processing_status")
    @JsonProperty("processing_status")
    private String processingStatus;

    @Column(name = "status")
    @JsonProperty("status")
    private String status;

    // Notes and additional information
    @Column(name = "notes", columnDefinition = "NTEXT")
    @JsonProperty("notes")
    private String notes;

    // Audit fields
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    @JsonProperty("updated_by")
    private Long updatedBy;

    /**
     * Customer Status Enum - Based on the 5-status system from Node.js implementation
     */
    public enum CustomerStatus {
        ACTIVE("active"),           // Closed - Customer contacted and renewed
        RENEWED("renewed"),         // Closed - Customer renewed their policy  
        NOT_INTERESTED("not_interested"), // Closed - Customer not interested
        NOT_REACHABLE("not_reachable"),   // Open - Customer could not be reached
        FOLLOW_UP("follow_up"),     // Open - Customer needs follow-up
        NOT_STARTED("not_started"); // Open - Customer not yet contacted

        private final String value;

        CustomerStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
        
        /**
         * Convert string value from database to enum
         */
        public static CustomerStatus fromString(String value) {
            if (value == null) return NOT_STARTED;
            for (CustomerStatus status : CustomerStatus.values()) {
                if (status.getValue().equalsIgnoreCase(value)) {
                    return status;
                }
            }
            // Default fallback
            return NOT_STARTED;
        }

        /**
         * Check if this status represents a closed customer
         */
        public boolean isClosed() {
            return this == ACTIVE || this == RENEWED || this == NOT_INTERESTED;
        }

        /**
         * Check if this status represents an open customer
         */
        public boolean isOpen() {
            return !isClosed();
        }

        /**
         * Convert string value to enum
         */
        public static CustomerStatus fromValue(String value) {
            for (CustomerStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return NOT_STARTED; // Default fallback
        }
    }

    /**
     * Pre-persist callback to set default values
     */
    @PrePersist
    protected void onCreate() {
        if (customerStatus == null) {
            customerStatus = CustomerStatus.NOT_STARTED;
        }
        if (isClosed == null) {
            isClosed = customerStatus.isClosed();
        }
        if (lastStatusUpdated == null) {
            lastStatusUpdated = LocalDateTime.now();
        }
    }

    /**
     * Pre-update callback to update status-related fields
     */
    @PreUpdate
    protected void onUpdate() {
        if (customerStatus != null) {
            isClosed = customerStatus.isClosed();
            lastStatusUpdated = LocalDateTime.now();
        }
    }

    /**
     * Get full customer name
     */
    @JsonIgnore
    public String getFullName() {
        return firstName != null ? firstName : "";
    }

    /**
     * Check if customer is currently assigned to a user
     */
    @JsonIgnore
    public boolean isAssigned() {
        return assignedTo != null;
    }

    /**
     * Check if customer is available for assignment (not closed and not assigned)
     */
    @JsonIgnore
    public boolean isAvailableForAssignment() {
        return !isClosed && assignedTo == null;
    }
}
