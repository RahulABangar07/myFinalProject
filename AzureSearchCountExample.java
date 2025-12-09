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
