package com.technova.campusdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The job only coordinates: it asks for candidates and closes them one by one, surviving individual failures. */
class TicketAutoCloseJobTests {

    @Test
    void closesEveryCandidateAndCountsTheOnesActuallyClosed() {
        TicketService service = mock(TicketService.class);
        when(service.findAutoCloseCandidates(any(Instant.class))).thenReturn(List.of(1L, 2L, 3L));
        when(service.autoClose(eq(1L), any(Duration.class))).thenReturn(true);
        when(service.autoClose(eq(2L), any(Duration.class))).thenReturn(false);   // changed in the meantime
        when(service.autoClose(eq(3L), any(Duration.class))).thenReturn(true);

        int closed = new TicketAutoCloseJob(service, 3).closeStaleTickets();

        assertThat(closed).isEqualTo(2);
    }

    @Test
    void aFailingTicketDoesNotStopTheOthers() {
        TicketService service = mock(TicketService.class);
        when(service.findAutoCloseCandidates(any(Instant.class))).thenReturn(List.of(1L, 2L));
        when(service.autoClose(eq(1L), any(Duration.class))).thenThrow(new IllegalStateException("lock"));
        when(service.autoClose(eq(2L), any(Duration.class))).thenReturn(true);

        int closed = new TicketAutoCloseJob(service, 3).closeStaleTickets();

        assertThat(closed).isEqualTo(1);
        verify(service).autoClose(eq(2L), any(Duration.class));
    }
}
