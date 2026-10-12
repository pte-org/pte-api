package com.pte.identity;

import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.service.ExamStaffAccountService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Authorized, safe read facade for the Host's staff workspace. */
@Service
public class ExamStaffIdentityService {
    private final ExamStaffAccountService accounts;
    public ExamStaffIdentityService(ExamStaffAccountService accounts) { this.accounts = accounts; }
    public ExamStaffAccountView get(UUID id, CurrentUser caller) { return accounts.get(id, caller); }
}
