package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // ===================== CONTROLLER =====================
    @RestController
    @RequestMapping("/users")
    public static class UserController {

        // ✅ V2 API → when header request-origin=mobile
        @GetMapping(headers = "request-origin=mobile")
        public ResponseEntity<?> getUsersV2() {
            return ResponseEntity.ok("Response from V2 API");
        }

        // ✅ Default → fallback (V1)
        @GetMapping
        public ResponseEntity<?> getUsersV1() {
            return ResponseEntity.ok("Response from V1 API");
        }
    }
}
