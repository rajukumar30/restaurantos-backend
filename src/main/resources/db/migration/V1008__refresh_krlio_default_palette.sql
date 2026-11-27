-- Only replace the previous unmodified CLASSIC palette; retain all other branding fields and custom palettes.
UPDATE restaurant_guest_branding
SET settings_json = settings_json || '{"primaryColor":"#213D32","accentColor":"#B64D32","backgroundColor":"#FAF7F2"}'::jsonb,
    updated_at = NOW()
WHERE settings_json->>'theme' = 'CLASSIC'
  AND UPPER(settings_json->>'primaryColor') = '#1B241F'
  AND UPPER(settings_json->>'accentColor') = '#E7764C'
  AND UPPER(settings_json->>'backgroundColor') = '#F7F7F4';
