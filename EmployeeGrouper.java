import java.util.*;

public class PrioritizedEmployeeGrouper {

    // Configurable tolerance property
    private static final double TOLERANCE_PERCENT = 5.0;

    // Simple flat POJO representing your input database records
    public static class EmployeeRecord {
        private final String employeeNumber;
        private final String groupId;

        public EmployeeRecord(String employeeNumber, String groupId) {
            this.employeeNumber = employeeNumber;
            this.groupId = groupId;
        }

        public String getEmployeeNumber() { return employeeNumber; }
        public String getGroupId() { return groupId; }
    }

    /**
     * ADAPTER METHOD: Converts a flat list of employee-group records into graph structures
     * and executes the bounded, prioritized partitioning.
     */
    public static void generateInputAndExecute(List<EmployeeRecord> flatRecords, double targetPercent) {
        Set<String> allEmployees = new HashSet<>();
        Set<String> allGroups = new HashSet<>();
        Map<String, List<String>> employeeToGroups = new HashMap<>();

        // Process flat list sequentially to build graph structures
        for (EmployeeRecord record : flatRecords) {
            if (record == null) continue;
            
            String emp = record.getEmployeeNumber();
            String grp = record.getGroupId();

            if (emp != null) allEmployees.add(emp);
            if (grp != null) allGroups.add(grp);

            if (emp != null && grp != null) {
                employeeToGroups.computeIfAbsent(emp, k -> new ArrayList<>()).add(grp);
            }
        }

        // Forward structural data maps to the core traversal logic
        divideEmployeesWithBridgePriority(employeeToGroups, allEmployees, allGroups, targetPercent);
    }

    /**
     * CORE ALGORITHM: Performs the bounded graph traversal with bridge prioritization
     */
    public static void divideEmployeesWithBridgePriority(
            Map<String, List<String>> employeeToGroups, 
            Set<String> allEmployees, 
            Set<String> allGroups,
            double targetPercent) {

        if (allGroups.isEmpty()) {
            System.out.println("No groups available to partition. All employees assigned to Group 2.");
            return;
        }

        // Step 1: Map Group -> List of Employees
        Map<String, List<String>> groupToEmployees = new HashMap<>();
        for (String group : allGroups) {
            groupToEmployees.put(group, new ArrayList<>());
        }
        for (Map.Entry<String, List<String>> entry : employeeToGroups.entrySet()) {
            String emp = entry.getKey();
            List<String> groups = entry.getValue();
            if (groups != null) {
                for (String grp : groups) {
                    if (groupToEmployees.containsKey(grp)) {
                        groupToEmployees.get(grp).add(emp);
                    }
                }
            }
        }

        // Step 2: Seed the execution from the first available group
        String seedGroup = allGroups.iterator().next();
        System.out.printf("Starting search from Seed Group: %s | Target: %.1f%% | Tolerance: %.1f%%%n%n", 
                seedGroup, targetPercent, TOLERANCE_PERCENT);

        Set<String> group1Employees = new HashSet<>();
        Set<String> visitedGroups = new HashSet<>();
        Queue<String> groupQueue = new LinkedList<>();

        groupQueue.add(seedGroup);
        visitedGroups.add(seedGroup);

        // Step 3: Queue processing with capacity controls and priority routing
        while (!groupQueue.isEmpty()) {
            String currentGroup = groupQueue.poll();
            List<String> rawGroupPool = groupToEmployees.getOrDefault(currentGroup, Collections.emptyList());
            
            int totalGroupSize = rawGroupPool.size();
            if (totalGroupSize == 0) continue;

            // Calculate target limits using percentage and tolerance rules
            int minAllowed = (int) Math.max(0, Math.floor(((targetPercent - TOLERANCE_PERCENT) / 100.0) * totalGroupSize));
            int maxAllowed = (int) Math.min(totalGroupSize, Math.ceil(((targetPercent + TOLERANCE_PERCENT) / 100.0) * totalGroupSize));
            int allowedCapacity = Math.max(minAllowed, Math.min(maxAllowed, (int) Math.round((targetPercent / 100.0) * totalGroupSize)));

            // Separate candidates and track who is already captured in Group 1
            List<String> bridgeCandidates = new ArrayList<>();
            List<String> regularCandidates = new ArrayList<>();
            int alreadyInGroup1Count = 0;

            for (String emp : rawGroupPool) {
                if (group1Employees.contains(emp)) {
                    alreadyInGroup1Count++;
                } else {
                    List<String> linkedGroups = employeeToGroups.getOrDefault(emp, Collections.emptyList());
                    if (linkedGroups != null && linkedGroups.size() > 1) {
                        bridgeCandidates.add(emp);
                    } else {
                        regularCandidates.add(emp);
                    }
                }
            }

            int quotaRemaining = allowedCapacity - alreadyInGroup1Count;

            if (quotaRemaining > 0) {
                // Keep selections random within their respective structural tiers
                Collections.shuffle(bridgeCandidates);
                Collections.shuffle(regularCandidates);

                List<String> prioritizedSelectionList = new ArrayList<>();
                prioritizedSelectionList.addAll(bridgeCandidates);
                prioritizedSelectionList.addAll(regularCandidates);

                int itemsToTake = Math.min(quotaRemaining, prioritizedSelectionList.size());

                for (int i = 0; i < itemsToTake; i++) {
                    String selectedEmp = prioritizedSelectionList.get(i);
                    group1Employees.add(selectedEmp);

                    // Track down cross-linked groups from chosen employee
                    List<String> linkedGroups = employeeToGroups.getOrDefault(selectedEmp, Collections.emptyList());
                    if (linkedGroups != null) {
                        for (String linkedGroup : linkedGroups) {
                            if (!visitedGroups.contains(linkedGroup)) {
                                visitedGroups.add(linkedGroup);
                                groupQueue.add(linkedGroup);
                            }
                        }
                    }
                }
            }
        }

        // Step 4: Leftovers fall back to Group 2
        Set<String> group2Employees = new HashSet<>();
        for (String emp : allEmployees) {
            if (!group1Employees.contains(emp)) {
                group2Employees.add(emp);
            }
        }

        // Print final outputs
        System.out.println("=== FINAL SELECTION OUTPUT ===");
        System.out.println("Group 1 Size (" + group1Employees.size() + "): " + group1Employees);
        System.out.println("Group 2 Size (" + group2Employees.size() + "): " + group2Employees);
    }

    public static void main(String[] args) {
        // Simulating flat database table rows where an employee appears multiple times if in multiple groups
        List<EmployeeRecord> flatDatabaseRecords = new ArrayList<>();
        
        flatDatabaseRecords.add(new EmployeeRecord("Emp1", "Group_X"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp2", "Group_X"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp4", "Group_X"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp5", "Group_X"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp6", "Group_X"));
        
        // Emp3 is the structural bridge linking Group_X and Group_Y
        flatDatabaseRecords.add(new EmployeeRecord("Emp3", "Group_X"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp3", "Group_Y")); 

        // Emp7 is the structural bridge linking Group_Y and Group_Z
        flatDatabaseRecords.add(new EmployeeRecord("Emp7", "Group_Y"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp7", "Group_Z"));
        
        flatDatabaseRecords.add(new EmployeeRecord("Emp8", "Group_Y"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp9", "Group_Z"));
        flatDatabaseRecords.add(new EmployeeRecord("Emp10", "Group_Z"));

        // Trigger input generation followed by graph partition with a 50% target
        double inputPercentageParam = 50.0; 
        generateInputAndExecute(flatDatabaseRecords, inputPercentageParam);
    }
}
