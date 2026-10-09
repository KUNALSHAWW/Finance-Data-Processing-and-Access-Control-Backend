package com.kunal.finance.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kunal.finance.backend.audit.AuditHasher;
import com.kunal.finance.backend.audit.AuditService;
import com.kunal.finance.backend.dto.Dtos.FinancialRecordRequest;
import com.kunal.finance.backend.dto.Dtos.FinancialRecordResponse;
import com.kunal.finance.backend.dto.Dtos.PaginatedResponse;
import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.entity.User;
import com.kunal.finance.backend.exception.ConflictException;
import com.kunal.finance.backend.exception.ResourceNotFoundException;
import com.kunal.finance.backend.repository.FinancialRecordRepository;
import com.kunal.finance.backend.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinancialRecordService {

    private static final String DEFAULT_CATEGORY = "Others";

    private final FinancialRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final AuditService audit;

    @Transactional
    public FinancialRecordResponse create(FinancialRecordRequest req, String actor) {
        User user = userRepository.findByEmail(actor)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        FinancialRecord record = recordRepository.save(FinancialRecord.builder()
                .amount(req.amount())
                .type(req.type())
                .category(categoryOf(req))
                .recordDate(req.date() != null ? req.date() : LocalDate.now(ZoneOffset.UTC))
                .description(blankToNull(req.description()))
                .createdBy(user)
                .build());
        auditChange(actor, "CREATED", record);
        return toResponse(record);
    }

    /** Any admin may correct any record; who changed what is preserved in the audit ledger, not by ownership. */
    @Transactional
    public FinancialRecordResponse update(Long id, FinancialRecordRequest req, String actor) {
        FinancialRecord record = findLive(id);
        record.setAmount(req.amount());
        record.setType(req.type());
        record.setCategory(categoryOf(req));
        if (req.date() != null) {
            record.setRecordDate(req.date());
        }
        record.setDescription(blankToNull(req.description()));
        record = recordRepository.saveAndFlush(record);
        auditChange(actor, "UPDATED", record);
        return toResponse(record);
    }

    @Transactional
    public void delete(Long id, String actor) {
        FinancialRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
        if (record.isDeleted()) {
            throw new ConflictException("Record is already deleted");
        }
        record.setDeleted(true);
        record.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        recordRepository.save(record);
        auditChange(actor, "DELETED", record);
    }

    @Transactional(readOnly = true)
    public FinancialRecordResponse get(Long id) {
        return toResponse(findLive(id));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<FinancialRecordResponse> list(int page, int size, RecordType type, String category,
            LocalDate from, LocalDate to) {
        Specification<FinancialRecord> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (type != null) {
                ps.add(cb.equal(root.get("type"), type));
            }
            if (category != null && !category.isBlank()) {
                ps.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("recordDate"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("recordDate"), to));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.desc("recordDate"), Sort.Order.desc("id"));
        Page<FinancialRecord> p = recordRepository.findAll(spec, PageRequest.of(page, size, sort));
        return new PaginatedResponse<>(p.getContent().stream().map(this::toResponse).toList(), p.getNumber(),
                p.getTotalPages(), p.getTotalElements());
    }

    private FinancialRecord findLive(Long id) {
        return recordRepository.findById(id).filter(r -> !r.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
    }

    private void auditChange(String actor, String action, FinancialRecord r) {
        audit.append(actor, action, "RECORD", r.getId(), AuditHasher.recordDigest(r),
                "amount=" + r.getAmount().toPlainString() + " type=" + r.getType() + " category=" + r.getCategory()
                        + " date=" + r.getRecordDate());
    }

    private static String categoryOf(FinancialRecordRequest req) {
        return req.category() == null || req.category().isBlank() ? DEFAULT_CATEGORY : req.category().trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private FinancialRecordResponse toResponse(FinancialRecord r) {
        return new FinancialRecordResponse(r.getId(), r.getAmount(), r.getType(), r.getCategory(), r.getRecordDate(),
                r.getDescription(), r.getCreatedBy().getEmail(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
