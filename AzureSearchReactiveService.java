@Service
public class AzureSearchReactiveService {

    private final SearchClient searchClient;

    public AzureSearchReactiveService(SearchClientBuilder builder) {
        this.searchClient = builder.buildClient();
    }

    public Mono<PaginatedSearchResponse> fetchPage(
            String filter,
            List<String> select,
            Map<String, String> displayNames,
            int offset,
            int length
    ) {
        SearchOptions options = new SearchOptions()
                .setFilter(filter)
                .setSkip(offset)
                .setTop(length)
                .setSelect(select);

        return Mono.fromCallable(() -> searchClient.search("*", options))
                .flatMap(result -> {

                    long totalCount = result.getTotalCount();

                    List<Map<String, Object>> rows = new ArrayList<>();

                    result.iterableByPage().forEach(page -> {
                        for (SearchResult r : page.getElements()) {
                            Map<String, Object> row = new HashMap<>();

                            r.getDocument(KeyValuePair.class).forEach((k, v) -> {
                                String displayKey = displayNames.getOrDefault(k, k);
                                row.put(displayKey, v);
                            });

                            rows.add(row);
                        }
                    });

                    return Mono.just(new PaginatedSearchResponse(
                            totalCount,
                            offset,
                            length,
                            displayNames,
                            rows
                    ));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
