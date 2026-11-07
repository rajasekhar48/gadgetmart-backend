package com.gadgetmart.notification.domain;

public enum NotificationStatus {
    PENDING,   // waiting to be (re)sent
    SENT,
    FAILED     // gave up after max attempts
}
