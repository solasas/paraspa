package com.sashank.paraspa.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards a lock that unit tests with mocks cannot exercise. Deleting a comment must take a real FOR UPDATE:
 * replies take FOR KEY SHARE on their parent, and PESSIMISTIC_WRITE would become FOR NO KEY UPDATE on PostgreSQL,
 * which does not conflict with it (a stress run showed commentCount drifting). Do not "simplify" this to @Lock.
 */
class CommentRepositoryContractTest {

    @Test
    void findByIdForUpdate_usesARealForUpdateNativeQuery() throws Exception {
        Method method = CommentRepository.class.getMethod("findByIdForUpdate", Long.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).containsIgnoringCase("FOR UPDATE").doesNotContainIgnoringCase("NO KEY");
    }

    @Test
    void findByIdForUpdate_doesNotRelyOnPessimisticWriteLockMode() throws Exception {
        Method method = CommentRepository.class.getMethod("findByIdForUpdate", Long.class);
        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock == null || lock.value() != LockModeType.PESSIMISTIC_WRITE).isTrue();
    }
}
