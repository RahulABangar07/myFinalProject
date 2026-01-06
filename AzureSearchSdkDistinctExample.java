package com.example.azuresearch;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.models.FacetResult;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* ===================================================== */
/* ===================== CONTROLLER ==================== */
/* ===================================================== */

@RestController
public class AzureSearchSdkDistinctExample {

    private final AnalyticsService analyticsService;

    public AzureSearchSdkDistinctExample(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/distinct-employees-by-market")
    public Mono<ResponseEntity<DistinctEmployeeResult>> distinctEmployeesByMarket() {
        return analyticsService.distinctEmployeesPerMarket()
                .map(result ->
                        ResponseEntity
                                .status(result.partial()
                                        ? HttpStatus.PARTIAL_CONTENT
                                        : HttpStatus.OK)
                                .body(result)
                );
    }
}

/* ===================================================== */
/* ===================== SERVICE ======================= */
/* ===================================================== */

@Service
class AnalyticsService {

    private static final int MAX_PARALLELISM = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final SearchClient searchClient;

    AnalyticsService() {
        this.searchClient =
                new SearchClientBuilder()
                        .endpoint("https://<YOUR-SEARCH-NAME>.search.windows.net")
                        .credential(new AzureKeyCredential("<YOUR-API-KEY>"))
                        .indexName("employees")
                        .buildClient();
    }

    /**
     * Main orchestration method
     */
    Mono<DistinctEmployeeResult> distinctEmployeesPerMarket() {
        return fetchDistinctMarkets()
                .flatMap(markets ->
                        Flux.fromIterable(markets)
                                .flatMap(market ->
                                                distinctEmployeeCount(market)
                                                        .timeout(TIMEOUT)
                                                        .map(count -> Map.entry(market, count))
                                                        .onErrorResume(ex ->
                                                                Mono.just(Map.entry(market, -1))
                                                        ),
                                        MAX_PARALLELISM
                                )
                                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                .map(this::buildResult)
                );
    }

    /**
     * Step 1: Fetch all distinct markets using facet
     */
    private Mono<List<String>> fetchDistinctMarkets() {
        return Mono.fromCallable(() -> {
                    SearchOptions options = new SearchOptions();
                    options.setTop(0);
                    options.getFacets().add("market");

                    return searchClient.search("*", options);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(results ->
                        results.getFacets()
                                .get("market")
                                .stream()
                                .map(FacetResult::getValue)
                                .map(Object::toString)
                                .collect(Collectors.toList())
                );
    }

    /**
     * Step 2: Distinct employeeId count for a market
     */
    private Mono<Integer> distinctEmployeeCount(String market) {
        return Mono.fromCallable(() -> {
                    SearchOptions options = new SearchOptions();
                    options.setTop(0);
                    options.setFilter("market eq '" + market + "'");
                    options.getFacets().add("employeeId,count:100000");

                    return searchClient.search("*", options);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(results ->
                        results.getFacets()
                                .get("employeeId")
                                .size()
                );
    }

    private DistinctEmployeeResult buildResult(Map<String, Integer> counts) {
        boolean partial = counts.values().stream().anyMatch(v -> v == -1);

        return new DistinctEmployeeResult(
                counts,
                partial,
                partial
                        ? "Partial result: some markets failed or timed out"
                        : "Complete result"
        );
    }
}

/* ===================================================== */
/* ===================== DTO ============================ */
/* ===================================================== */

record DistinctEmployeeResult(
        Map<String, Integer> counts,
        boolean partial,
        String message
) {}
