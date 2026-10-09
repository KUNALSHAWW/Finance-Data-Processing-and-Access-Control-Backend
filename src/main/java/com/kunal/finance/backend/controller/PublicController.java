package com.kunal.finance.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.demo.DemoGuard;
import com.kunal.finance.backend.demo.DemoData;
import com.kunal.finance.backend.dto.Dtos.PublicConfig;
import com.kunal.finance.backend.dto.Dtos.VisitRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public", description = "Unauthenticated: front-end config and anonymous visit counting")
public class PublicController {

    private final DemoGuard demo;

    @GetMapping("/config")
    @Operation(summary = "Is this the public demo? If so, which sample accounts can be used?")
    public PublicConfig config() {
        return new PublicConfig(demo.enabled(), demo.enabled() ? DemoData.ACCOUNTS : List.of());
    }

    /**
     * Anonymous campaign counter: logs the utm_* labels of a tagged link, nothing else (no IP, no cookies, no ids).
     * Lets the owner see in the platform logs which application link was opened.
     */
    @PostMapping("/visit")
    @Operation(summary = "Record that a tagged link was opened (utm labels only)")
    public ResponseEntity<Void> visit(@Valid @RequestBody VisitRequest v) {
        // ponytail: no rate limit; a flood only fills the log, add a per-IP bucket if that ever happens
        log.info("VISIT source={} medium={} campaign={} content={} term={} path={}", clean(v.source()),
                clean(v.medium()), clean(v.campaign()), clean(v.content()), clean(v.term()), clean(v.path()));
        return ResponseEntity.noContent().build();
    }

    /** Keeps log lines unforgeable: only a small safe alphabet gets through. */
    private static String clean(String s) {
        return s == null || s.isBlank() ? "-" : s.replaceAll("[^A-Za-z0-9_.:/@ +-]", "?");
    }
}
