package com.leafy.iottestdataservice.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcReferenceSeedRepository implements ReferenceSeedRepository {

    private static final String INSERT_USER_SQL = """
        INSERT INTO users (id)
        VALUES (:id)
        ON CONFLICT DO NOTHING
        """;

    private static final String INSERT_FARM_PLOT_SQL = """
        INSERT INTO farm_plots (id)
        VALUES (:id)
        ON CONFLICT DO NOTHING
        """;

    private static final String INSERT_ZONE_SQL = """
        INSERT INTO farm_zones (id)
        VALUES (:id)
        ON CONFLICT DO NOTHING
        """;

    private static final String FIND_SENSOR_TYPE_ID_SQL = """
        SELECT id
        FROM sensor_types
        WHERE code = :code
        """;

    private static final String INSERT_SENSOR_TYPE_SQL = """
        INSERT INTO sensor_types (id, code, name, unit, min_default, max_default, description, created_at)
        VALUES (:id, :code, :name, :unit, :minDefault, :maxDefault, :description, :createdAt)
        ON CONFLICT (code) DO NOTHING
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public boolean ensureUser(String userId) {
        return insertIdOnly(INSERT_USER_SQL, userId);
    }

    @Override
    public boolean ensureFarmPlot(String farmPlotId) {
        return insertIdOnly(INSERT_FARM_PLOT_SQL, farmPlotId);
    }

    @Override
    public boolean ensureZone(String zoneId) {
        return insertIdOnly(INSERT_ZONE_SQL, zoneId);
    }

    @Override
    public Optional<UUID> findSensorTypeIdByCode(String code) {
        return jdbcTemplate.query(FIND_SENSOR_TYPE_ID_SQL, Map.of("code", code), rs -> rs.next() ? Optional.of(readUuid(rs)) : Optional.empty());
    }

    @Override
    public boolean insertSensorType(
        UUID id,
        String code,
        String name,
        String unit,
        Double minDefault,
        Double maxDefault,
        String description,
        Instant createdAt
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("code", code)
            .addValue("name", name)
            .addValue("unit", unit)
            .addValue("minDefault", minDefault)
            .addValue("maxDefault", maxDefault)
            .addValue("description", description)
            .addValue(
                "createdAt",
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE
            );
        return jdbcTemplate.update(INSERT_SENSOR_TYPE_SQL, parameters) > 0;
    }

    private boolean insertIdOnly(String sql, String id) {
        return jdbcTemplate.update(sql, Map.of("id", id)) > 0;
    }

    private UUID readUuid(ResultSet resultSet) throws SQLException {
        Object value = resultSet.getObject("id");
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }
}
