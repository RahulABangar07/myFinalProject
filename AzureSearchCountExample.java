import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.models.SearchOptions;
import reactor.core.publisher.Flux;

public class AzureSearchReactiveExample {

    public static void main(String[] args) {
        var client = new SearchClientBuilder()
                .endpoint("<your-search-endpoint>")
                .credential(new AzureKeyCredential("<api-key>"))
                .indexName("<index-name>")
                .buildSearchAsyncClient();   // <-- Reactive client

        var idsToSearch = List.of("a", "b", "c");

        String filter = String.format(
                "tags/any(t: search.in(t, '%s'))",
                String.join(",", idsToSearch)
        );

        SearchOptions opts = new SearchOptions()
                .setFilter(filter)
                .setSelect("id", "id2", "assets");

        Flux<SearchResult> flux = client.search(null, opts)
                .flatMap(response -> response.getResults());

        flux.collectList()
                .map(results -> {
                    // Reactive sum of assets
                    double totalAssets = results.stream()
                            .map(r -> (Number) r.getDocument(Map.class).get("assets"))
                            .mapToDouble(Number::doubleValue)
                            .sum();
                    
                    return Map.of(
                            "results", results,
                            "sumAssets", totalAssets
                    );
                })
                .subscribe(System.out::println);
    }
}

import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.SearchPagedFlux;
import com.azure.search.documents.SearchQueryOptions;
import com.azure.search.documents.SearchServiceVersion;
import com.azure.search.documents.indexes.SearchIndexAsyncClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

public class SearchAggregationService {

    private final SearchIndexAsyncClient client;

    public SearchAggregationService(String endpoint, String key, String indexName) {
        this.client = new SearchIndexClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .serviceVersion(SearchServiceVersion.V2023_07_01)
                .buildSearchIndexAsyncClient(indexName);
    }

    public Mono<AggregatedResult> aggregateForTypes(List<String> inputTypes) {
        // Convert input list to CSV for search.in
        String typeCsv = inputTypes.stream()
                .map(t -> t.replace("'", "''"))
                .collect(Collectors.joining(","));

        // Build filter for array field `types`
        String filter = String.format("types/any(t: search.in(t, '%s'))", typeCsv);

        SearchQueryOptions options = new SearchQueryOptions()
                .setFilter(filter)
                .setSelect("assetValue", "employeeIds", "householdIds")   // fetch only required fields
                .setTop(1000); // page size

        SearchPagedFlux results = client.search(null, options);

        return results
                .byPage()
                .flatMap(page -> Flux.fromIterable(page.getValue()))
                .flatMap(result -> {
                    SearchDocument doc = result.getDocument(SearchDocument.class);

                    List<String> employeeIds = doc.getList("employeeIds", String.class);
                    List<String> householdIds = doc.getList("householdIds", String.class);

                    Double assetValue = doc.getDouble("assetValue");
                    if (assetValue == null) assetValue = 0.0;

                    return Mono.just(new Partial(
                            employeeIds == null ? 0 : employeeIds.size(),
                            householdIds == null ? 0 : householdIds.size(),
                            assetValue
                    ));
                })
                .collect(
                        () -> new AggregatedResult(0, 0, 0.0),
                        (acc, partial) -> {
                            acc.employeeCount += partial.employeeCount;
                            acc.householdCount += partial.householdCount;
                            acc.assetValueSum += partial.assetValueSum;
                        }
                );
    }

    // Aggregation models
    record Partial(int employeeCount, int householdCount, double assetValueSum) {}

    public static class AggregatedResult {
        public int employeeCount;
        public int householdCount;
        public double assetValueSum;

        public AggregatedResult(int employeeCount, int householdCount, double assetValueSum) {
            this.employeeCount = employeeCount;
            this.householdCount = householdCount;
            this.assetValueSum = assetValueSum;
        }
    }
}

