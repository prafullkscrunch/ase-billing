-- Renames 'JSW containers and Seal Scanning Charges' to 'Containers Seal
-- Scanning Charges' — the charge itself, its rate, and its rate history
-- are all unchanged. Safe as a plain rename because service_rates
-- references services by service_id, never by name (see V1__schema.sql),
-- so no historical rate row needs touching.
--
-- Both the display name and the printed-line template change, since the
-- template repeats the name: 'JSW containers and Seal Scanning Charges
-- Rs.{rate} PER TEU' -> 'Containers Seal Scanning Charges Rs.{rate} PER TEU'.

UPDATE services
SET name = 'Containers Seal Scanning Charges',
    print_template = 'Containers Seal Scanning Charges Rs.{rate} PER TEU'
WHERE name = 'JSW containers and Seal Scanning Charges';
