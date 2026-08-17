package com.bbc.sms.academic;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the asynchronous bulletin batch authorization boundary.
 * The worker must retain the authenticated creator while rendering and
 * registering each student document, then restore/clear the worker context.
 */
class ReportCardBatchJobWorkerSecurityContextContractTest {

    @Test
    void batchServiceCapturesCreatorAuthenticationBeforeAsyncDispatch() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/academic/ReportCardBatchJobService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("var authentication = SecurityContextHolder.getContext().getAuthentication();")
                .contains("worker.start(id, schoolId, authentication)")
                .doesNotContain("worker.start(id, schoolId);");
    }

    @Test
    void workerInstallsAndRestoresSecurityContextAroundTheJob() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/academic/ReportCardBatchJobWorker.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("SecurityContext previousContext = SecurityContextHolder.getContext();")
                .contains("SecurityContext workerContext = SecurityContextHolder.createEmptyContext();")
                .contains("workerContext.setAuthentication(authentication);")
                .contains("SecurityContextHolder.setContext(workerContext);")
                .contains("SecurityContextHolder.clearContext();")
                .contains("SecurityContextHolder.setContext(previousContext);");
    }
}
