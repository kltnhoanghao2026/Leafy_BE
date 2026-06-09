package com.leafy.searchservice.services.failedevent;

import com.leafy.common.model.kafka.EventType;
import com.leafy.searchservice.dto.response.FailedEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FailedEventService {

    void logFailure(String eventId,
                    EventType eventType,
                    String topic,
                    Integer partition,
                    Long offset,
                    String payload,
                    String errorMessage,
                    String stackTrace,
                    int retryCount);

    Page<FailedEventResponse> getEventsByResolved(Boolean resolved, String keyword, Integer hours, Pageable pageable);

    long countEventsByResolved(boolean resolved);

    void updateResolved(String id, boolean resolved);

    FailedEventResponse getEventById(String id);

    void retryEvent(String id);

    void retryEvents(List<String> ids);

    void retryAllEvents();

    void retryEventsByDuration(int hours);
}
