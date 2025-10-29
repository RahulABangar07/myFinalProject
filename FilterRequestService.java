package com.example.filters.service;

import com.example.filters.model.FilterRequest;
import com.example.filters.repository.FilterRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FilterRequestService {

    private final FilterRequestRepository repository;

    public Mono<FilterRequest> save(FilterRequest request) {
        return repository.save(request);
    }

    public Mono<FilterRequest> getById(String id) {
        return repository.findById(id);
    }

    public Flux<FilterRequest> getByNumber(String number) {
        return repository.findByNumber(number);
    }

    public Flux<FilterRequest> getAll() {
        return repository.findAll();
    }
}
