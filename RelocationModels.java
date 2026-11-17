package com.enterprise.mcp.model;

public class RelocationModels {

    public record EmployeeCommuteProfile(
            String employeeId,
            String name,
            String currentArea,
            double distanceKm,
            double monthlyCommuteCost
    ) {}

    public record RelocationPolicyRecommendation(
            String employeeId,
            String recommendedPolicyName,
            double distanceFromOfficeKm,
            double monthlyHousingStipend,
            double commuteCostEliminated,
            double netCompanyMonthlyImpact,
            String rationale
    ) {}
}
