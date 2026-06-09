package com.leafy.authservice.service.seeder;

import java.util.Map;

public interface AccountSeederService {
    Map<String, Object> seedAccounts(int count);
}
