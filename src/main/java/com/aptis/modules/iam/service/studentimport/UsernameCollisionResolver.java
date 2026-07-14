package com.aptis.modules.iam.service.studentimport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.constant.IamMessageConstants;
import com.aptis.modules.iam.repository.StudentRepository;

@Service
public class UsernameCollisionResolver {

    private static final int QUERY_CHUNK_SIZE = 1000;
    private static final int PRE_FETCH_SUFFIX_CAP = 50;

    private final StudentRepository studentRepository;

    public UsernameCollisionResolver(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public List<ResolvedUsername> resolve(List<String> usernameBases) {
        Set<String> reservedUsernames = new LinkedHashSet<>(findExistingUsernames(usernameBases));
        List<ResolvedUsername> resolvedUsernames = new ArrayList<>();

        for (String usernameBase : usernameBases) {
            resolvedUsernames.add(resolveOne(usernameBase, reservedUsernames));
        }

        return resolvedUsernames;
    }

    private ResolvedUsername resolveOne(String usernameBase, Set<String> reservedUsernames) {
        String candidate = usernameBase;
        int suffix = 2;

        while (reservedUsernames.contains(candidate)
                && suffix <= IamApiConstants.USERNAME_COLLISION_SUFFIX_LIMIT) {
            candidate = usernameBase + "_" + suffix;
            suffix++;
        }

        if (reservedUsernames.contains(candidate)
                || studentRepository.existsByUsername(candidate)) {
            return new ResolvedUsername(
                    usernameBase,
                    null,
                    IamMessageConstants.USERNAME_COLLISION_UNRESOLVED,
                    false);
        }

        reservedUsernames.add(candidate);
        return new ResolvedUsername(
                usernameBase,
                candidate,
                null,
                !candidate.equals(usernameBase));
    }

    private Set<String> findExistingUsernames(List<String> usernameBases) {
        int maxSuffix = Math.min(usernameBases.size() + 2, PRE_FETCH_SUFFIX_CAP);
        Set<String> candidates = new LinkedHashSet<>();
        for (String usernameBase : usernameBases) {
            candidates.add(usernameBase);
            for (int s = 2; s <= maxSuffix; s++) {
                candidates.add(usernameBase + "_" + s);
            }
        }

        Set<String> existingUsernames = new LinkedHashSet<>();
        List<String> values = new ArrayList<>(candidates);
        for (int index = 0; index < values.size(); index += QUERY_CHUNK_SIZE) {
            Set<String> chunk = new LinkedHashSet<>(
                    values.subList(index, Math.min(index + QUERY_CHUNK_SIZE, values.size())));
            if (!chunk.isEmpty()) {
                existingUsernames.addAll(studentRepository.findExistingUsernames(chunk));
            }
        }
        return existingUsernames;
    }

    public record ResolvedUsername(
            String usernameBase,
            String generatedUsername,
            String errorCode,
            boolean collisionResolved) {
    }
}
