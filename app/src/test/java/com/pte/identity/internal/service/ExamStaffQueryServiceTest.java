package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.InvalidExamStaffQueryException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamStaffQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void search_filtersAndMapsExamStaffWithBoundedPageRequest() {
        UUID tenantId = UUID.randomUUID();
        User staff = new User();
        staff.setPublicId(UUID.randomUUID());
        staff.setUsername("alice@example.test");
        staff.setEmail("alice@example.test");
        staff.setFullName("Alice Examiner");
        staff.setTenantId(tenantId);
        staff.setStatus(UserStatus.ACTIVE);
        staff.setRoles(Set.of(Role.EXAMINER));
        when(userRepository.findPageForExamStaff(eq(tenantId), anySet(), eq(Role.EXAMINER), eq(UserStatus.ACTIVE),
                eq("alice"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(staff), PageRequest.of(2, 100), 201));

        ExamStaffQueryService service = new ExamStaffQueryService(userRepository);
        var result = service.search(2, 500, " Alice ", "examiner", "active", "full_name", "asc",
                new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN")));

        assertThat(result.data()).singleElement().extracting(UserResponse::fullName).isEqualTo("Alice Examiner");
        assertThat(result.meta().page()).isEqualTo(2);
        assertThat(result.meta().size()).isEqualTo(100);
        assertThat(result.meta().totalElements()).isEqualTo(201);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findPageForExamStaff(eq(tenantId), anySet(), eq(Role.EXAMINER), eq(UserStatus.ACTIVE),
                eq("alice"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("fullName").isAscending()).isTrue();
    }

    @Test
    void search_rejectsNonExamStaffRoleFilter() {
        ExamStaffQueryService service = new ExamStaffQueryService(userRepository);

        assertThatThrownBy(() -> service.search(0, 20, null, "student", null, null, null,
                new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"))))
                .isInstanceOf(InvalidExamStaffQueryException.class);
    }
}
