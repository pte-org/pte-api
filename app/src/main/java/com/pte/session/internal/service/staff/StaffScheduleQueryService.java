package com.pte.session.internal.service.staff;

import com.pte.session.StaffSessionFilter;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionProctor;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.StaffSessionView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.UUID;

/** Session-owned dynamic predicates; no nullable timestamp SQL parameters or cross-module joins. */
@Service
public class StaffScheduleQueryService {
    private final EntityManager entityManager;
    public StaffScheduleQueryService(EntityManager entityManager) { this.entityManager = entityManager; }

    public Page<StaffSessionView> page(UUID tenantId, UUID proctorId, StaffSessionFilter filter,
            int page, int size) {
        var direction = filter.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(page, size, Sort.by(direction, "opensAt").and(Sort.by("publicId")));
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createTupleQuery();
        var root = query.from(ExamSession.class);
        query.multiselect(root.get("publicId"), root.get("name"), root.get("sessionCode"),
                root.get("status"), root.get("opensAt"), root.get("closesAt"));
        query.where(predicate(root, query, builder, tenantId, proctorId, filter));
        query.orderBy(filter.ascending() ? builder.asc(root.get("opensAt")) : builder.desc(root.get("opensAt")),
                builder.asc(root.get("publicId")));
        var countQuery = builder.createQuery(Long.class);
        var countRoot = countQuery.from(ExamSession.class);
        countQuery.select(builder.count(countRoot)).where(predicate(countRoot, countQuery, builder,
                tenantId, proctorId, filter));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        var rows = entityManager.createQuery(query).setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(size).getResultList().stream().map(this::view).toList();
        return new PageImpl<>(rows, pageable, total);
    }

    private StaffSessionView view(Tuple row) {
        return new StaffSessionView(row.get(0, UUID.class), row.get(1, String.class), row.get(2, String.class),
                row.get(3, SessionStatus.class).name(), row.get(4, java.time.Instant.class),
                row.get(5, java.time.Instant.class));
    }

    private Predicate predicate(Root<ExamSession> root, CriteriaQuery<?> query, CriteriaBuilder builder,
            UUID tenantId, UUID proctorId, StaffSessionFilter filter) {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            predicates.add(builder.isFalse(root.get("deleted")));
            if (filter.sessionPublicId() != null) {
                predicates.add(builder.equal(root.get("publicId"), filter.sessionPublicId()));
            }
            if (filter.sessionStatus() != null) {
                predicates.add(builder.equal(root.get("status"), SessionStatus.valueOf(filter.sessionStatus())));
            }
            if (filter.from() != null || filter.to() != null) {
                predicates.add(builder.lessThan(root.get("opensAt"), root.get("closesAt")));
            }
            if (filter.from() != null) { predicates.add(builder.greaterThan(root.get("closesAt"), filter.from())); }
            if (filter.to() != null) { predicates.add(builder.lessThan(root.get("opensAt"), filter.to())); }
            if (proctorId != null) {
                var assigned = query.subquery(Long.class);
                var assignment = assigned.from(ExamSessionProctor.class);
                assigned.select(assignment.get("id")).where(
                        builder.equal(assignment.get("session"), root),
                        builder.equal(assignment.get("tenantId"), tenantId),
                        builder.equal(assignment.get("proctorPublicId"), proctorId),
                        builder.isFalse(assignment.get("deleted")));
                predicates.add(builder.exists(assigned));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
    }
}
