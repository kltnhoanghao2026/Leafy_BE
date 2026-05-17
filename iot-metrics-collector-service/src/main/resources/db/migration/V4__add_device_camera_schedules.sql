create table if not exists device_camera_schedules (
    id uuid primary key,
    device_uid varchar(100) not null,
    enabled boolean not null default true,
    trigger_type varchar(30) not null,
    time_of_day time not null,
    recurrence varchar(30) not null,
    last_run_at timestamp with time zone,
    next_run_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone
);

create index if not exists idx_device_camera_schedules_device_enabled
    on device_camera_schedules(device_uid, enabled);

create index if not exists idx_device_camera_schedules_enabled_next_run
    on device_camera_schedules(enabled, next_run_at);
