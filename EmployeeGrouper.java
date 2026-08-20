import java.util.*;

public class EmployeeGrouper {

    public static void divideEmployees(
            Map<String, List<String>> employeeToGroups, 
            Set<String> allEmployees, 
            Set<String> allGroups) {

        // Edge case: No groups exist in the system
        if (allGroups.isEmpty()) {
            System.out.println("No groups available to partition. All employees assigned to Group 2.");
            System.out.println("Group 1: []");
            System.out.println("Group 2: " + allEmployees + "\n");
            return;
        }

        // Step 1: Build inverse mapping (Group -> List of Employees)
        Map<String, List<String>> groupToEmployees = new HashMap<>();
        for (String group : allGroups) {
            groupToEmployees.put(group, new ArrayList<>());
        }
        for (Map.Entry<String, List<String>> entry : employeeToGroups.entrySet()) {
            String emp = entry.getKey();
            List<String> groups = entry.getValue();
            
            // Clean handling for employees with no groups (null or empty lists)
            if (groups != null) {
                for (String grp : groups) {
                    if (groupToEmployees.containsKey(grp)) {
                        groupToEmployees.get(grp).add(emp);
                    }
                }
            }
        }

        // Step 2: Pick the first group randomly/sequentially as the seed
        String seedGroup = allGroups.iterator().next();
        System.out.println("Starting recursive search from Seed Group: " + seedGroup);

        Set<String> group1Employees = new HashSet<>();
        Set<String> visitedGroups = new HashSet<>();
        Queue<String> groupQueue = new LinkedList<>();

        // Initialize queue with seed group
        groupQueue.add(seedGroup);
        visitedGroups.add(seedGroup);

        // Step 3: Breadth-First Search (BFS) for recursive linking
        while (!groupQueue.isEmpty()) {
            String currentGroup = groupQueue.poll();
            List<String> employeesInGroup = groupToEmployees.getOrDefault(currentGroup, Collections.emptyList());

            for (String emp : employeesInGroup) {
                // Try adding employee to Group 1
                if (group1Employees.add(emp)) {
                    // If newly discovered, find all groups they tag into
                    List<String> linkedGroups = employeeToGroups.getOrDefault(emp, Collections.emptyList());
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

        // Step 4: Remainder fallback (unlinked or group-less employees go to Group 2)
        Set<String> group2Employees = new HashSet<>();
        for (String emp : allEmployees) {
            if (!group1Employees.contains(emp)) {
                group2Employees.add(emp);
            }
        }

        // Print final distribution
        System.out.println("=== FINAL PARTITION ===");
        System.out.println("Group 1: " + group1Employees);
        System.out.println("Group 2: " + group2Employees + "\n");
    }

    public static void main(String[] args) {
        // Master list definition
        Set<String> allEmployees = new HashSet<>(Arrays.asList(
            "EmpA", "EmpB", "EmpC", "EmpD", "EmpIsolated", "EmpUniversal"
        ));
        Set<String> allGroups = new HashSet<>(Arrays.asList("X", "Y", "Z"));

        // Setup relationship mapping
        Map<String, List<String>> employeeToGroups = new HashMap<>();
        
        // Regular entries
        employeeToGroups.put("EmpA", Arrays.asList("X"));
        employeeToGroups.put("EmpB", Arrays.asList("Y"));
        employeeToGroups.put("EmpC", Arrays.asList("Z"));

        // EDGE CASE 1: Employee connected to absolutely NO groups
        employeeToGroups.put("EmpIsolated", Collections.emptyList()); 

        // EDGE CASE 2: Employee connected to ALL groups (Acts as a structural bridge)
        employeeToGroups.put("EmpUniversal", Arrays.asList("X", "Y", "Z")); 

        System.out.println("--- TEST 1: With Universal Bridge ---");
        divideEmployees(employeeToGroups, allEmployees, allGroups);

        System.out.println("--- TEST 2: Without Universal Bridge (Isolated Removed Connection) ---");
        // Sever the bridge to demonstrate ordinary network isolation
        employeeToGroups.put("EmpUniversal", Collections.emptyList()); 
        divideEmployees(employeeToGroups, allEmployees, allGroups);
    }
}
