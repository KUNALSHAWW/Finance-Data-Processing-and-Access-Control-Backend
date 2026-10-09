package com.kunal.finance.backend.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kunal.finance.backend.audit.AuditHasher;
import com.kunal.finance.backend.audit.AuditService;
import com.kunal.finance.backend.dto.Dtos.PaginatedResponse;
import com.kunal.finance.backend.dto.Dtos.UserRequest;
import com.kunal.finance.backend.dto.Dtos.UserResponse;
import com.kunal.finance.backend.entity.User;
import com.kunal.finance.backend.exception.ConflictException;
import com.kunal.finance.backend.exception.ResourceNotFoundException;
import com.kunal.finance.backend.repository.FinancialRecordRepository;
import com.kunal.finance.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FinancialRecordRepository recordRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    @Transactional
    public UserResponse create(UserRequest request, String actor) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        User user = userRepository.save(User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .active(true)
                .build());
        audit.append(actor, "CREATED", "USER", user.getId(), AuditHasher.userDigest(user),
                "email=" + email + " role=" + user.getRole());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<UserResponse> list(int page, int size) {
        Page<User> p = userRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        List<UserResponse> data = p.getContent().stream().map(this::toResponse).toList();
        return new PaginatedResponse<>(data, p.getNumber(), p.getTotalPages(), p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public String deactivate(Long id, String actor) {
        User user = find(id);
        if (user.getEmail().equals(actor)) {
            throw new ConflictException("You cannot deactivate your own account");
        }
        if (!user.isActive()) {
            return "User is already inactive";
        }
        user.setActive(false);
        audit.append(actor, "DEACTIVATED", "USER", id, AuditHasher.userDigest(user), "email=" + user.getEmail());
        return "User deactivated successfully";
    }

    @Transactional
    public String activate(Long id, String actor) {
        User user = find(id);
        if (user.isActive()) {
            return "User is already active";
        }
        user.setActive(true);
        audit.append(actor, "ACTIVATED", "USER", id, AuditHasher.userDigest(user), "email=" + user.getEmail());
        return "User activated successfully";
    }

    @Transactional
    public String delete(Long id, String actor) {
        User user = find(id);
        if (user.getEmail().equals(actor)) {
            throw new ConflictException("You cannot delete your own account");
        }
        if (recordRepository.existsByCreatedById(id)) {
            throw new ConflictException("User owns financial records; deactivate the account instead of deleting it");
        }
        userRepository.delete(user);
        audit.append(actor, "DELETED", "USER", id, null, "email=" + user.getEmail());
        return "User deleted successfully";
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isActive());
    }
}
