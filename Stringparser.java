import java.util.*;

public class StringParser {

    enum Type {
        MARKET,
        DIVISION,
        BRANCH
    }

    public static Map<String, Set<String>> buildMap(List<String> data, Type type) {
        Map<String, Set<String>> result = new HashMap<>();

        for (String entry : data) {
            String[] parts = entry.split("_");
            if (parts.length < 4) continue;

            String value = parts[0];        // 12345
            String divid = parts[1];        // divid
            String marketId = parts[2];     // marketid
            String branchId = parts[3];     // branchid

            String key;

            switch (type) {
                case MARKET:
                    key = marketId;
                    break;
                case BRANCH:
                    key = branchId;
                    break;
                case DIVISION:
                default:
                    key = divid;
            }

            result.computeIfAbsent(key, k -> new HashSet<>()).add(value);
        }

        return result;
    }

    public static void main(String[] args) {
        List<String> list = Arrays.asList(
            "12345_DIV1_MKT1_BR1",
            "12346_DIV1_MKT1_BR1",
            "12345_DIV2_MKT2_BR2"
        );

        Map<String, Set<String>> map = buildMap(list, Type.DIVISION);
        System.out.println(map);
    }
}
