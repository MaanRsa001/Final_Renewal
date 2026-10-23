package com.insurance.renewal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * READ-ONLY mapping of ewayglobal.motor_driver_details (only DRIVER_DOB is needed).
 * The table is only ever read through projection queries (never loaded as an entity),
 * so QUOTE_NO is declared as the @Id purely to satisfy JPA.
 */
@Entity
@Table(name = "motor_driver_details")
@Getter @Setter @NoArgsConstructor
public class MotorDriverDetails {

    @Id @Column(name = "QUOTE_NO")
    private String quoteNo;

    @Column(name = "DRIVER_DOB")
    private LocalDate driverDob;
}
