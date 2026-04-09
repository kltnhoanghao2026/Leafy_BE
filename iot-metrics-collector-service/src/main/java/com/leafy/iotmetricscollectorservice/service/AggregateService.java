package com.leafy.iotmetricscollectorservice.service;

import java.time.Instant;

public interface AggregateService {
    void rebuild5mWindow(Instant from, Instant to);

    void rebuild1hWindow(Instant from, Instant to);

    void rebuild1dWindow(Instant from, Instant to);
}
