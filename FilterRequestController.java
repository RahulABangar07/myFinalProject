package com.example.filters.controller;

import com.example.filters.model.FilterRequest;
import com.example.filters.service.FilterRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/filters")
@RequiredArgsConstructor
public class FilterRequestController {

    private final FilterRequestService service;

    @PostMapping
    public Mono<FilterRequest> createFilter(@RequestBody FilterRequest request) {
        return service.save(request);
    }

    @GetMapping("/{id}")
    public Mono<FilterRequest> getById(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping("/number/{number}")
    public Flux<FilterRequest> getByNumber(@PathVariable String number) {
        return service.getByNumber(number);
    }

    @GetMapping
    public Flux<FilterRequest> getAll() {
        return service.getAll();
    }
}
