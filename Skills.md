# Skill: Employee Commute & Relocation Optimization

## Overview
This skill enables an autonomous AI agent to identify employees commuting from a specific residential area and calculate an optimal near-office relocation policy that offsets their existing commute expenses.

## Architecture & Endpoints
- **MCP Server**: `relocation-mcp-server` (`http://localhost:8080`)
- **Transport**: Streamable HTTP / SSE (`/mcp`)
- **Agent Host**: `relocation-agent-service` (`http://localhost:8081`)

---

## Exposed MCP Tools

### 1. `find_employees_by_area`
* **Description**: Retrieves all employees currently travelling to the office from a user-specified residential area or locality.
* **Input Schema**:
  * `area` (`string`, required): Name of the residential locality (e.g., `"Hinjewadi"`, `"Kharadi"`, `"Baner"`).
* **Output Schema**: `List<EmployeeCommuteProfile>`
  * `employeeId` (`string`): Unique employee identifier.
  * `name` (`string`): Full name of the employee.
  * `currentArea` (`string`): Residential area.
  * `distanceKm` (`double`): One-way distance to the office in kilometers.
  * `monthlyCommuteCost` (`double`): Current monthly travel/cab reimbursement cost in INR.

### 2. `recommend_relocation_policy`
* **Description**: Evaluates an employee's monthly commute expenditure against near-office housing tiers (within 3 km of the office) and selects the policy with the highest net cost offset.
* **Input Schema**:
  * `employeeId` (`string`, required): Unique employee identifier.
  * `monthlyCommuteCost` (`double`, required): Current monthly commute cost in INR.
* **Output Schema**: `RelocationPolicyRecommendation`
  * `employeeId` (`string`)
  * `recommendedPolicyName` (`string`): Tier name (`Tier-1 Studio`, `Tier-2 Shared 2BHK`, or `Tier-3 PG/Co-Living`).
  * `distanceFromOfficeKm` (`double`): Distance of the proposed housing from the office.
  * `monthlyHousingStipend` (`double`): Company-paid housing allowance in INR.
  * `commuteCostEliminated` (`double`): Monthly commute expense saved in INR.
  * `netCompanyMonthlyImpact` (`double`): `commuteCostEliminated - monthlyHousingStipend` (positive indicates net savings; negative indicates net investment).
  * `rationale` (`string`): Explanation of why this tier was selected.

---

## Agent Execution Workflow
1. **Trigger**: User submits a target residential area via REST API.
2. **Discovery**: Agent invokes `find_employees_by_area(area)`.
3. **Evaluation**: For every employee returned, the agent invokes `recommend_relocation_policy(employeeId, monthlyCommuteCost)`.
4. **Synthesis**: Agent compiles a structured markdown summary detailing employee savings, recommended housing tiers, and total budget impact.
