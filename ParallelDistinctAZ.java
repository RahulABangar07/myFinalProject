import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchAsyncClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchPagedResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

public class ParallelDistinctAZ {

    private static final int PAGE_SIZE = 2000;
    private static final int PARALLELISM = 16;   // FAST but safe

    public static void main(String[] args) {

        String endpoint = "<endpoint>";
        String apiKey = "<apiKey>";
        String indexName = "<indexName>";

        List<String> fields = Arrays.asList(
                "City", "State", "Segment", "Type", "Status"
        );

        SearchAsyncClient client = new SearchClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .indexName(indexName)
                .buildAsyncClient();

        // Build prefix list for a-z AND A-Z
        List<String> prefixes = buildAZazPrefixes();

        long start = System.currentTimeMillis();

        Mono<Map<String, Set<Object>>> result =
                Flux.fromIterable(fields)
                        .flatMap(field -> processField(client, field, prefixes), PARALLELISM)
                        .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()));

        Map<String, Set<Object>> output = result.block();

        long end = System.currentTimeMillis();

        System.out.println("Distinct values for all fields (completed in " + (end - start) + " ms)");

        output.forEach((fld, set) -> {
            System.out.println("\nFIELD: " + fld);
            System.out.println("COUNT: " + set.size());
            set.forEach(System.out::println);
        });
    }


    // Generate all prefixes: a..z and A..Z
    private static List<String> buildAZazPrefixes() {
        List<String> prefixes = new ArrayList<>(52);

        for (char c = 'a'; c <= 'z'; c++)
            prefixes.add(String.valueOf(c));

        for (char c = 'A'; c <= 'Z'; c++)
            prefixes.add(String.valueOf(c));

        return prefixes;
    }


    // Process 1 field across 52 partitions
    private static Mono<Map.Entry<String, Set<Object>>> processField(
            SearchAsyncClient client,
            String field,
            List<String> prefixes
    ) {
        return Flux.fromIterable(prefixes)
                .flatMap(prefix -> scanPartition(client, field, prefix), PARALLELISM)
                .filter(Objects::nonNull)
                .collect(HashSet::new, Set::add)
                .map(distinct -> Map.entry(field, distinct));
    }

    // Scan one prefix partition (e.g. startswith(City, 'a'))
    private static Flux<Object> scanPartition(SearchAsyncClient client, String field, String prefix) {

        SearchOptions opt = new SearchOptions()
                .setSelect(field)
                .setFilter(String.format("startswith(%s, '%s')", field, prefix))
                .setTop(PAGE_SIZE);

        return client.search("*", opt)
                .byPage()
                .flatMapIterable(SearchPagedResponse::getValue)
                .map(r -> r.getDocument(Map.class).get(field));
    }

}
