alter table device_media_analysis
    add column if not exists request_id varchar(100),
    add column if not exists trigger_type varchar(40),
    add column if not exists severity varchar(40),
    add column if not exists disease_type varchar(255),
    add column if not exists captured_at timestamp with time zone;

create index if not exists idx_device_media_analysis_request_id
    on device_media_analysis(request_id);
