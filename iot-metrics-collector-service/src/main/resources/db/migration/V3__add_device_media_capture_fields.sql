alter table device_media_events
    add column if not exists status varchar(50),
    add column if not exists request_id varchar(100),
    add column if not exists content_type varchar(100),
    add column if not exists size_bytes bigint,
    add column if not exists width integer,
    add column if not exists height integer,
    add column if not exists error varchar(500),
    add column if not exists requested_at timestamp with time zone,
    add column if not exists command_sent_at timestamp with time zone,
    add column if not exists uploaded_at timestamp with time zone;

create unique index if not exists uk_device_media_events_request_id
    on device_media_events(request_id)
    where request_id is not null;

create index if not exists idx_device_media_events_device_requested_at
    on device_media_events(device_id, requested_at desc);

create index if not exists idx_device_media_events_status_requested_at
    on device_media_events(status, requested_at);
