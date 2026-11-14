package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.repository.ServiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proposal selection: authorisation, repeats, rollback and concurrency. These run on H2 (the standard test database);
 * the PostgreSQL variant is scripts/check-selection-postgres.cjs and has to be run against a disposable database.
 */
class SelectionIntegrityIntegrationTests extends BackendTestSupport {

    @MockitoSpyBean ServiceRepository serviceRepositorySpy;
    @PersistenceContext EntityManager entityManager;

    @Test
    void acceptingTheSameProposalTwiceKeepsASingleService() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        long proposal = submit(request(owner, type), tech);
        accept(proposal, owner);

        mvc.perform(post("/api/proposals/{id}/accept", proposal).header("Authorization", bearer(owner))).andExpect(status().isConflict());
        assertThat(services.count()).isEqualTo(1);
    }

    @Test
    void onlyTheOwningClientCanAccept() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        long proposal = submit(request(owner, type), tech);

        mvc.perform(post("/api/proposals/{id}/accept", proposal)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/proposals/{id}/accept", proposal).header("Authorization", bearer(tech))).andExpect(status().isForbidden());
        mvc.perform(post("/api/proposals/{id}/accept", proposal).header("Authorization", bearer(user(Role.CLIENT)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/proposals/{id}/accept", 987654L).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        mvc.perform(post("/api/proposals/abc/accept").header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        assertThat(services.count()).isZero();
        assertThat(proposals.findById(proposal).orElseThrow().getStatus()).isEqualTo(Proposal.Status.PENDING);
    }

    @Test
    void aFailureWhileCreatingTheServiceRollsBackEverythingTheSelectionChanged() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        Request request = request(owner, type);
        long chosen = submit(request, technician(type, LAT, LON));
        long other = submit(request, technician(type, LAT, LON));

        // Write the service and flush every pending update inside the selection transaction, then fail: the
        // proposal statuses, the request status and the new service row are all in the database when it blows up.
        AtomicBoolean flushed = new AtomicBoolean();
        doAnswer(invocation -> {
            entityManager.persist(invocation.<ServiceEntity>getArgument(0));
            entityManager.flush();
            flushed.set(true);
            throw new IllegalStateException("forced failure after flush");
        }).when(serviceRepositorySpy).saveAndFlush(any(ServiceEntity.class));

        mvc.perform(post("/api/proposals/{id}/accept", chosen).header("Authorization", bearer(owner)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(flushed).isTrue();
        assertThat(services.count()).isZero();
        assertThat(proposals.findById(chosen).orElseThrow().getStatus()).isEqualTo(Proposal.Status.PENDING);
        assertThat(proposals.findById(other).orElseThrow().getStatus()).isEqualTo(Proposal.Status.PENDING);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(Request.RequestStatus.CON_PROPUESTAS);
    }

    @Test
    void concurrentAcceptanceOfDifferentProposalsCreatesExactlyOneService() throws Exception {
        for (int round = 0; round < 3; round++) {
            User owner = user(Role.CLIENT);
            ApplianceType type = type();
            Request request = request(owner, type);
            long first = submit(request, technician(type, LAT, LON));
            long second = submit(request, technician(type, LAT, LON));

            assertThat(race(owner, first, second)).containsExactlyInAnyOrder(201, 409);
            assertExactlyOneSelection(request);
        }
    }

    @Test
    void concurrentAcceptanceOfTheSameProposalCreatesExactlyOneService() throws Exception {
        for (int round = 0; round < 3; round++) {
            User owner = user(Role.CLIENT);
            ApplianceType type = type();
            Request request = request(owner, type);
            long proposal = submit(request, technician(type, LAT, LON));
            submit(request, technician(type, LAT, LON));

            assertThat(race(owner, proposal, proposal)).containsExactlyInAnyOrder(201, 409);
            assertExactlyOneSelection(request);
        }
    }

    @Test
    void acceptingWhileTheOwnerCancelsLeavesEitherACancelledRequestOrAContractNeverBoth() throws Exception {
        for (int round = 0; round < 3; round++) {
            User owner = user(Role.CLIENT);
            ApplianceType type = type();
            Request request = request(owner, type);
            long proposal = submit(request, technician(type, LAT, LON));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            String auth = bearer(owner);
            Future<Integer> accept = pool.submit(() -> {
                go.await();
                return mvc.perform(post("/api/proposals/{id}/accept", proposal).header("Authorization", auth)).andReturn().getResponse().getStatus();
            });
            Future<Integer> cancel = pool.submit(() -> {
                go.await();
                return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/requests/{id}/cancel", request.getId()).header("Authorization", auth)).andReturn().getResponse().getStatus();
            });
            go.countDown();
            int acceptStatus = accept.get(30, TimeUnit.SECONDS);
            int cancelStatus = cancel.get(30, TimeUnit.SECONDS);
            pool.shutdown();

            Request.RequestStatus finalStatus = requests.findById(request.getId()).orElseThrow().getStatus();
            if (acceptStatus == 201) {
                assertThat(cancelStatus).isEqualTo(409);
                assertThat(finalStatus).isEqualTo(Request.RequestStatus.CERRADA);
                assertThat(services.findByRequestId(request.getId())).isPresent();
            } else {
                assertThat(acceptStatus).isEqualTo(409);
                assertThat(cancelStatus).isEqualTo(200);
                assertThat(finalStatus).isEqualTo(Request.RequestStatus.CANCELADA);
                assertThat(services.findByRequestId(request.getId())).isEmpty();
            }
        }
    }

    private void assertExactlyOneSelection(Request request) {
        assertThat(services.findByRequestId(request.getId())).isPresent(); // unique(request_id): never more than one
        List<Proposal> all = proposals.findByRequestIdOrderByDiagnosticCostAscAvailableAtAsc(request.getId());
        assertThat(all.stream().filter(p -> p.getStatus() == Proposal.Status.ACCEPTED)).hasSize(1);
        assertThat(all.stream().filter(p -> p.getStatus() == Proposal.Status.PENDING)).isEmpty();
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(Request.RequestStatus.CERRADA);
    }

    /** Fires one accept call per id at the same instant and returns the HTTP statuses. */
    private List<Integer> race(User owner, long... proposalIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(proposalIds.length);
        CountDownLatch ready = new CountDownLatch(proposalIds.length);
        CountDownLatch go = new CountDownLatch(1);
        String auth = bearer(owner);
        List<Future<Integer>> calls = new ArrayList<>();
        for (long proposalId : proposalIds) {
            calls.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return mvc.perform(post("/api/proposals/{id}/accept", proposalId).header("Authorization", auth))
                        .andReturn().getResponse().getStatus();
            }));
        }
        ready.await(30, TimeUnit.SECONDS);
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> call : calls) statuses.add(call.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        return statuses;
    }
}
