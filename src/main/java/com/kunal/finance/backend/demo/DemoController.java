package com.kunal.finance.backend.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.dto.Dtos.MessageResponse;
import com.kunal.finance.backend.dto.Dtos.TamperResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Not registered at all unless app.demo.enabled=true. */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@Tag(name = "Demo", description = "Public demo only: simulate tampering, then restore")
public class DemoController {

    private final DemoService demo;

    @PostMapping("/tamper")
    @Operation(summary = "Edit a record directly in the database, bypassing the API",
            description = "Simulates someone with database access hiding an expense. Run /api/audit/verify afterwards.")
    public ResponseEntity<TamperResult> tamper() {
        return ResponseEntity.ok(demo.tamper());
    }

    @PostMapping("/reset")
    @Operation(summary = "Restore the demo data and a fresh audit chain")
    public ResponseEntity<MessageResponse> reset() {
        demo.reset();
        return ResponseEntity.ok(new MessageResponse("SUCCESS", "Demo data restored"));
    }
}
