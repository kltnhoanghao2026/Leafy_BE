alter table device_camera_schedules
    add column if not exists resolution varchar(30),
    add column if not exists quality varchar(30),
    add column if not exists upload_endpoint varchar(1000);

update device_camera_schedules
set resolution = coalesce(resolution, 'VGA'),
    quality = coalesce(quality, 'MEDIUM')
where resolution is null
   or quality is null;
