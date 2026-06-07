-- Demo data for the catalog. Idempotent-ish: only seeds an empty events table.
INSERT INTO venues (id, name, address, total_capacity) VALUES
  ('11111111-1111-1111-1111-111111111111', 'The Grand Arena',        '123 Main St, Springfield',  20000),
  ('22222222-2222-2222-2222-222222222222', 'Riverside Amphitheater', '45 River Rd, Portland',      8000),
  ('33333333-3333-3333-3333-333333333333', 'Blue Note Hall',         '9 Jazz Ave, New Orleans',    1200)
ON CONFLICT (id) DO NOTHING;

INSERT INTO events (id, title, artist, event_date, venue_id, status) VALUES
  ('a0000001-0000-4000-8000-000000000001', 'Midnight Echoes Live',   'Midnight Echoes', now() + interval '21 days', '11111111-1111-1111-1111-111111111111', 'PUBLISHED'),
  ('a0000002-0000-4000-8000-000000000002', 'Acoustic Sunset',        'Lila Moreno',     now() + interval '35 days', '22222222-2222-2222-2222-222222222222', 'PUBLISHED'),
  ('a0000003-0000-4000-8000-000000000003', 'Late Night Jazz Session','The Blue Quartet',now() + interval '14 days', '33333333-3333-3333-3333-333333333333', 'PUBLISHED'),
  ('a0000004-0000-4000-8000-000000000004', 'Stadium Rock Night',     'Iron Vega',       now() + interval '60 days', '11111111-1111-1111-1111-111111111111', 'PUBLISHED'),
  ('a0000005-0000-4000-8000-000000000005', 'Secret Warm-up Show',    'Iron Vega',       now() + interval '7 days',  '33333333-3333-3333-3333-333333333333', 'DRAFT')
ON CONFLICT (id) DO NOTHING;
