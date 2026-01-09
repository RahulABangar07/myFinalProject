@Service
public class SearchAggregationService {

    private final SearchClient searchClient;

    public SearchAggregationService(SearchClient searchClient) {
        this.searchClient = searchClient;
    }

    /**
     * Unique count via facet
     */
    private Mono<Long> uniqueCount(String fieldName) {
        return Mono.fromCallable(() -> {

            SearchOptions options = new SearchOptions()
                .setTop(0)
                .setFacets(Collections.singletonList(
                    fieldName + ",count:1000000"
                ));

            SearchPagedIterable results =
                searchClient.search("*", options, Context.NONE);

            List<FacetResult> facets =
                results.getFacets().get(fieldName);

            return facets == null ? 0L : (long) facets.size();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Sum aggregation using $apply
     */
    private Mono<Double> sumAssets() {
        return Mono.fromCallable(() -> {

            SearchOptions options = new SearchOptions()
                .setTop(0)
                .setFilter(null)
                .setOrderBy(Collections.emptyList())
                .setIncludeTotalCount(false)
                .setSearchFields(Collections.emptyList())
                .setSelect(Collections.emptyList())
                .setSearchMode(SearchMode.ALL)
                .setQueryType(QueryType.SIMPLE);

            // SDK does not yet expose aggregate() directly → use raw parameter
            options.getRawParameters()
                .put("$apply", "aggregate(assets with sum as totalAssets)");

            SearchPagedIterable results =
                searchClient.search("*", options, Context.NONE);

            Map<String, Object> doc =
                results.iterator().next().getDocument(Map.class);

            return ((Number) doc.get("totalAssets")).doubleValue();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Parallel aggregation execution
     */
    public Mono<AggregationResult> fetchAggregates() {

        Mono<Long> clientCount =
            uniqueCount("clientId");

        Mono<Long> employeeCount =
            uniqueCount("employeeId");

        Mono<Double> totalAssets =
            sumAssets();

        return Mono.zip(clientCount, employeeCount, totalAssets)
            .map(t -> new AggregationResult(
                t.getT1(),
                t.getT2(),
                t.getT3()
            ));
    }
}
