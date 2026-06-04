alter table device_media_events
    add column if not exists deleted_at timestamp with time zone,
    add column if not exists deleted_by varchar(255);

create index if not exists idx_device_media_events_device_active_requested_at
    on device_media_events(device_id, requested_at desc)
    where deleted_at is null;

create index if not exists idx_device_media_events_zone_active_captured_at
    on device_media_events(zone_id, captured_at desc)
    where deleted_at is null;
