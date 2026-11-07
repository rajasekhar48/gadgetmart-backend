package com.gadgetmart.order.dto;

public record AddressResponse(String fullName, String phone, String line1, String line2,
                              String city, String state, String pincode) {
}
