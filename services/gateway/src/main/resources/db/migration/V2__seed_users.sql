-- Test drivers for the PoC. Password for every user: safedrive-poc (bcrypt). Dummy local data, not secrets.
INSERT INTO users (user_id, email, password_hash, scopes, status) VALUES
  ('00000000-0000-0000-0000-000000000001', 'driver1@safedrive.test', '$2y$10$xqxn2svzqRCsKbMlrnhVY.XFxiv4xnQnZmjeddQ6ErroljSrK6jkW',
   'telemetry:write score:read premium:read rewards:read claims:read', 'active'),
  ('00000000-0000-0000-0000-000000000002', 'driver2@safedrive.test', '$2y$10$xqxn2svzqRCsKbMlrnhVY.XFxiv4xnQnZmjeddQ6ErroljSrK6jkW',
   'telemetry:write score:read premium:read rewards:read claims:read', 'active'),
  ('00000000-0000-0000-0000-000000000003', 'readonly@safedrive.test', '$2y$10$xqxn2svzqRCsKbMlrnhVY.XFxiv4xnQnZmjeddQ6ErroljSrK6jkW',
   'score:read', 'active'),
  ('00000000-0000-0000-0000-000000000004', 'disabled@safedrive.test', '$2y$10$xqxn2svzqRCsKbMlrnhVY.XFxiv4xnQnZmjeddQ6ErroljSrK6jkW',
   'telemetry:write score:read premium:read rewards:read claims:read', 'disabled')
ON CONFLICT (email) DO NOTHING;
