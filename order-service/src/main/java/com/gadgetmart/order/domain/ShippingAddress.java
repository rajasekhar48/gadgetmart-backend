package com.gadgetmart.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {

    @Column(name = "ship_full_name", nullable = false)
    private String fullName;

    @Column(name = "ship_phone", nullable = false)
    private String phone;

    @Column(name = "ship_line1", nullable = false)
    private String line1;

    @Column(name = "ship_line2")
    private String line2;

    @Column(name = "ship_city", nullable = false)
    private String city;

    @Column(name = "ship_state", nullable = false)
    private String state;

    @Column(name = "ship_pincode", nullable = false)
    private String pincode;
}
