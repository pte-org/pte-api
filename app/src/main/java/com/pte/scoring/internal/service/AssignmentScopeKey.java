package com.pte.scoring.internal.service;

import com.pte.scoring.domain.enums.AssignmentScopeType;

import java.util.UUID;

record AssignmentScopeKey(AssignmentScopeType type, UUID scopePublicId) {
}
