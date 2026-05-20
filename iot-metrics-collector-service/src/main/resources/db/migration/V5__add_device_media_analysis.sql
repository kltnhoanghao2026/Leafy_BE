create table if not exists device_media_analysis (
    id uuid primary key,
    media_event_id uuid not null unique references device_media_events(id),
    alert_event_id uuid references alert_events(id),
    file_id varchar(255) not null,
    device_uid varchar(100) not null,
    status varchar(40) not null,
    disease_detected boolean not null default false,
    disease_name varchar(255),
    confidence double precision,
    notes text,
    file_url text,
    analyzed_at timestamp with time zone,
    error text,
    created_at timestamp with time zone,
    updated_at timestamp with time zone
);

create index if not exists idx_device_media_analysis_media_event
    on device_media_analysis(media_event_id);

create index if not exists idx_device_media_analysis_file_id
    on device_media_analysis(file_id);

create index if not exists idx_device_media_analysis_device_status
    on device_media_analysis(device_uid, status);
