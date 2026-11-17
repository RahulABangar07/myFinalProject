package com.enterprise.mcp.tools;

import com.enterprise.mcp.model.RelocationModels.EmployeeCommuteProfile;
import com.enterprise.mcp.model.RelocationModels.RelocationPolicyRecommendation;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RelocationMcpTools {

    // Simulated HR & Transport dataset (Office located in Balewadi High Street, Pune)
    private static final List<EmployeeCommuteProfile> EMPLOYEE_DATA = List.of(
            new EmployeeCommuteProfile("EMP-101", "Aarav Kulkarni", "Hinjewadi", 14.5, 16500.0),
            new EmployeeCommuteProfile("EMP-102", "Sneha Deshmukh", "Hinjewadi", 16.0, 18000.0),
            new EmployeeCommuteProfile("EMP-103", "Rohan Joshi", "Kharadi", 24.0, 24000.0),
            new EmployeeCommuteProfile("EMP-104", "Priya Nair", "Kharadi", 22.5, 21500.0),
            new EmployeeCommuteProfile("EMP-105", "Vikram Patil", "Wakad", 8.5, 9500.0),
            new EmployeeCommuteProfile("EMP-106", "Ananya Sharma", "Hadapsar", 26.0, 25000.0)
    );

    @Tool(
        name = "find_employees_by_area",
        description = "Finds all employees currently commuting to the office from a user-specified residential area."
    )
    public List<EmployeeCommuteProfile> findEmployeesByArea(
            @ToolParam(description = "Name of the residential area or locality (e.g., Hinjewadi, Kharadi, Wakad)") String area) {
        
        if (area == null || area.isBlank()) {
            return List.of();
        }
        String normalized = area.trim().toLowerCase();
        return EMPLOYEE_DATA.stream()
                .filter(emp -> emp.currentArea().toLowerCase().contains(normalized))
                .toList();
    }

    @Tool(
        name = "recommend_relocation_policy",
        description = "Calculates the best near-office relocation housing policy (within 3 km of office) that compensates the employee's monthly commute cost."
    )
    public RelocationPolicyRecommendation recommendRelocationPolicy(
            @ToolParam(description = "Unique employee ID") String employeeId,
            @ToolParam(description = "Current monthly commute cost in INR") double monthlyCommuteCost) {

        // Policy Tiers near Balewadi Office (within 3 km radius)
        // Tier-1 Studio Apartment: 22,000 INR/month (1.2 km from office)
        // Tier-2 Shared 2BHK:      15,000 INR/month (2.0 km from office)
        // Tier-3 Co-Living/PG:     10,000 INR/month (2.8 km from office)

        if (monthlyCommuteCost >= 22000.0) {
            double stipend = 22000.0;
            return new RelocationPolicyRecommendation(
                    employeeId,
                    "Tier-1 Near-Office Studio Policy",
                    1.2,
                    stipend,
                    monthlyCommuteCost,
                    monthlyCommuteCost - stipend,
                    "High commute expense fully covers a private 1BHK/Studio within 1.2 km of the office with net company savings."
            );
        } else if (monthlyCommuteCost >= 15000.0) {
            double stipend = 15000.0;
            return new RelocationPolicyRecommendation(
                    employeeId,
                    "Tier-2 Shared 2BHK Corporate Lease Policy",
                    2.0,
                    stipend,
                    monthlyCommuteCost,
                    monthlyCommuteCost - stipend,
                    "Commute savings completely offset a corporate-leased shared 2BHK within 2 km of the office."
            );
        } else {
            double stipend = 10000.0;
            return new RelocationPolicyRecommendation(
                    employeeId,
                    "Tier-3 Executive Co-Living Policy",
                    2.8,
                    stipend,
                    monthlyCommuteCost,
                    monthlyCommuteCost - stipend,
                    "Moderate commute cost is best offset using the subsidized Co-Living policy within 2.8 km of the office."
            );
        }
    }
}
