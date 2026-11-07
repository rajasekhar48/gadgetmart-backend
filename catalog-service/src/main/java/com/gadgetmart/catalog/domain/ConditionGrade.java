package com.gadgetmart.catalog.domain;

public enum ConditionGrade {
    A("Like new"),
    B("Good"),
    C("Fair");

    private final String label;

    ConditionGrade(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
