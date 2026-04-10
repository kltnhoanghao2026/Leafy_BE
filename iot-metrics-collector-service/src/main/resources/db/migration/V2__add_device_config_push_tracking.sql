ALTER TABLE device_configs
    ADD COLUMN IF NOT EXISTS last_push_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_push_error TEXT,
    ADD COLUMN IF NOT EXISTS last_ack_at TIMESTAMPTZ;
