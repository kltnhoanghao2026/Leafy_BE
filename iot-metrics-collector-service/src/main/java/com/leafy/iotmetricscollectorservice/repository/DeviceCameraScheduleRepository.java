package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.entity.DeviceCameraSchedule;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

/**
 * Repository for automatic camera capture schedules.
 */
public interface DeviceCameraScheduleRepository extends JpaRepository<DeviceCameraSchedule, UUID> {

    /**
     * Finds a schedule that belongs to the requested device UID. Client scoped
     * endpoints use this to prevent path traversal across devices.
     */
    Optional<DeviceCameraSchedule> findByIdAndDeviceUid(UUID id, String deviceUid);

    List<DeviceCameraSchedule> findAllByDeviceUidOrderByTimeOfDayAsc(String deviceUid);

    /**
     * Finds due schedules for the scheduler runner.
     *
     * @param now current UTC instant
     * @return enabled schedules with nextRunAt earlier than now
     */
    List<DeviceCameraSchedule> findAllByEnabledTrueAndNextRunAtBefore(Instant now);

    /**
     * Returns only due schedule ids so runner scans do not hold entity state
     * outside the per-schedule lock transaction.
     */
    @Query("""
        select schedule.id
        from DeviceCameraSchedule schedule
        where schedule.enabled = true
          and schedule.nextRunAt < :now
        """)
    List<UUID> findDueScheduleIds(Instant now);

    /**
     * Locks one schedule row for update inside the current transaction.
     *
     * <p>The lock timeout is zero so a second collector instance does not wait
     * behind the instance that already owns this due slot; it simply skips this
     * schedule and continues with other work.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select schedule from DeviceCameraSchedule schedule where schedule.id = :scheduleId")
    Optional<DeviceCameraSchedule> findLockedById(UUID scheduleId);
}
