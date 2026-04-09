package com.leafy.iotmetricscollectorservice.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.leafy.iotmetricscollectorservice.service.AggregateService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateSchedulerTest {

    @Mock
    private AggregateService aggregateService;

    @InjectMocks
    private AggregateScheduler aggregateScheduler;

    @Test
    void rebuild5mAggregates_callsAggregateServiceWithFifteenMinuteWindow() {
        Instant before = Instant.now();

        aggregateScheduler.rebuild5mAggregates();

        Instant after = Instant.now();
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(aggregateService).rebuild5mWindow(fromCaptor.capture(), toCaptor.capture());
        assertWindow(fromCaptor.getValue(), toCaptor.getValue(), Duration.ofMinutes(15), before, after);
    }

    @Test
    void rebuild1hAggregates_callsAggregateServiceWithThreeHourWindow() {
        Instant before = Instant.now();

        aggregateScheduler.rebuild1hAggregates();

        Instant after = Instant.now();
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(aggregateService).rebuild1hWindow(fromCaptor.capture(), toCaptor.capture());
        assertWindow(fromCaptor.getValue(), toCaptor.getValue(), Duration.ofHours(3), before, after);
    }

    @Test
    void rebuild1dAggregates_callsAggregateServiceWithThreeDayWindow() {
        Instant before = Instant.now();

        aggregateScheduler.rebuild1dAggregates();

        Instant after = Instant.now();
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(aggregateService).rebuild1dWindow(fromCaptor.capture(), toCaptor.capture());
        assertWindow(fromCaptor.getValue(), toCaptor.getValue(), Duration.ofDays(3), before, after);
    }

    @Test
    void rebuild5mAggregates_catchesExceptions() {
        doThrow(new IllegalStateException("boom")).when(aggregateService).rebuild5mWindow(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> aggregateScheduler.rebuild5mAggregates());
    }

    @Test
    void rebuild1hAggregates_catchesExceptions() {
        doThrow(new IllegalStateException("boom")).when(aggregateService).rebuild1hWindow(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> aggregateScheduler.rebuild1hAggregates());
    }

    @Test
    void rebuild1dAggregates_catchesExceptions() {
        doThrow(new IllegalStateException("boom")).when(aggregateService).rebuild1dWindow(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> aggregateScheduler.rebuild1dAggregates());
    }

    private void assertWindow(Instant from, Instant to, Duration expectedDuration, Instant before, Instant after) {
        assertEquals(expectedDuration, Duration.between(from, to));
        assertFalse(to.isBefore(before));
        assertFalse(from.isBefore(before.minus(expectedDuration)));
        assertFalse(to.isAfter(after));
    }
}
