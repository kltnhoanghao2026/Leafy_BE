package com.leafy.iottestdataservice.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class DeterministicIdFactory {

    private DeterministicIdFactory() {
    }

    public static UUID fromKey(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
