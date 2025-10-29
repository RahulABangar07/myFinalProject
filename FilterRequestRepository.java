package com.example.filters.repository;

import com.azure.spring.data.cosmos.repository.ReactiveCosmosRepository;
import com.example.filters.model.FilterRequest;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface FilterRequestRepository extends ReactiveCosmosRepository<FilterRequest, String> {

    Flux<FilterRequest> findByNumber(String number);
}
