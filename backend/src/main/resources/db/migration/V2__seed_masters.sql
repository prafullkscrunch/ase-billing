-- Seed data taken from the two supplied bill PDFs (370-434 and 437-480).
-- Rates are as billed in July/August 2026 and are effective_from 2026-04-01.

INSERT INTO company_settings
 (name, address_line1, address_line2, address_line3, city, pin, gstin,
  bank_name, bank_account_no, bank_ifsc, bank_branch_lines, default_hsn_code)
VALUES
 ('APRAMEYA SHIPPING ENTERPRISES',
  '# 12, MIG1, SFS 707, Experimental House',
  '3rd Cross, 4Th Phase, Yelahanka New Town',
  NULL, 'BANGALORE', '560 064', '29ADFPV3518A1ZM',
  'STATE BANK OF INDIA', '40705094297', 'SBIN0018237',
  'YELAHANKA 4TH PHASE BRANCH\n#722, CHS 707 SCHEME\n4TH PHASE MAIN ROAD\nYELAHANKA NEW TOWN\nBANGALORE-560064',
  '996713');

INSERT INTO customers (code, name, address_line1, address_line2, address_line3, city, state, pin, gstin, gst_treatment)
VALUES ('HC', 'HANGAL COFFEE EXPORTING PVT LTD',
        'N0 1095, JCST KOPPAL, 1ST STAGE',
        'C AND D BLOCK, KUVEMPUNAGARA',
        'CHAMARAJA MOHALLA',
        'MYSORE', 'Karnataka', '570023', '29AAECH4869B1ZG', 'INTRA');

INSERT INTO service_categories (code, description, pdf_layout, requires_shipment, sort_order) VALUES
 ('CNF', 'Clearing & Forwarding', 'ITEMISED',  TRUE,  1),
 ('T',   'Transportation',        'TRANSPORT', TRUE,  2),
 ('TA',  'Taxi Hire',             'ITEMISED',  TRUE,  3),
 ('S',   'Service',               'STATEMENT', FALSE, 4),
 ('CB',  'Export Incentive Claim','STATEMENT', FALSE, 5);

SET @cnf := (SELECT id FROM service_categories WHERE code='CNF');
SET @t   := (SELECT id FROM service_categories WHERE code='T');
SET @ta  := (SELECT id FROM service_categories WHERE code='TA');
SET @s   := (SELECT id FROM service_categories WHERE code='S');
SET @cb  := (SELECT id FROM service_categories WHERE code='CB');

-- CNF charge vocabulary. sort_order reproduces the order these print on the bills.
INSERT INTO services (category_id, name, print_template, calculation_type, default_unit, sort_order) VALUES
 (@cnf,'Pre shipment export documentation charges','Pre shipment export documentation charges','MANUAL',NULL,10),
 (@cnf,'ICO/Permit, ROC Submission & Self-sealing Documentation','ICO/Permit, ROC Submission & Self-sealing Documentation','MANUAL',NULL,20),
 (@cnf,'Expenses on Phytosanitary certificate','Expenses on Phytosanitary certificate','MANUAL',NULL,30),
 (@cnf,'Phytosanitary additional declaration','Phytosanitary addl. declaration','MANUAL',NULL,35),
 (@cnf,'Fumigation charges','Fumigation charges per TEU @ RS {rate}/-','PER_TEU','TEU',40),
 (@cnf,'Halting charges','Halting charges {days} days per TEU per day @Rs {rate}/-','PER_TEU_PER_DAY','TEU-day',45),
 (@cnf,'Certificate of weight & quality','Certificate of weight & quality','MANUAL',NULL,50),
 (@cnf,'Certificate of origin','Certificate of origin','MANUAL',NULL,60),
 (@cnf,'Health Certificate','Health Certificate','MANUAL',NULL,65),
 (@cnf,'Reissue of Health Certificate','Reissue of Health Certificate as per INSTR.','MANUAL',NULL,66),
 (@cnf,'Moisture Certificate','Moisture Certificate','MANUAL',NULL,67),
 (@cnf,'NON-GMO Certificate','NON- GMO CERTIFICATE','MANUAL',NULL,68),
 (@cnf,'REX/GSP','REX/GSP','MANUAL',NULL,69),
 (@cnf,'JSW containers and Seal Scanning Charges','JSW containers and Seal Scanning Charges Rs.{rate} PER TEU','PER_TEU','TEU',70),
 (@cnf,'Seal Charges','Seal Charges per TEU RS. {rate}/-','PER_TEU','TEU',71),
 (@cnf,'Dry Air Bags','Dry Air Bags PER TEU {rate}','PER_TEU','TEU',72),
 (@cnf,'Handling Charges','Handling Charges','MANUAL',NULL,73),
 (@cnf,'BlueDart Charges','BlueDart Charges','MANUAL',NULL,74),
 (@cnf,'EDI charges','EDI charges','MANUAL',NULL,80),
 (@cnf,'Empty Container survey fee','Empty Container survey fee','MANUAL',NULL,90),
 (@cnf,'Survey fee','Survey fee','MANUAL',NULL,91),
 (@cnf,'Open Examination Charges','Open Examination Charges','MANUAL',NULL,92),
 (@cnf,'Customs Clearance charges','Customs Clearance charges @{rate} PER TEU','PER_TEU','TEU',100),
 (@cnf,'Port Expenses','Port Expenses @{rate} PER TEU','PER_TEU','TEU',110),
 (@cnf,'VGM expenses','VGM expenses {qty}@RS{rate}/TEU','PER_TEU','TEU',120),
 (@cnf,'LO/LO','LO/LO @{rate} PER TEU','PER_TEU','TEU',130),
 (@cnf,'CHA Service charges','CHA Service charges @{rate} PER TEU','PER_TEU','TEU',140),
 (@cnf,'Coffee Stuffing Supervision and Photograph charges','Coffee Stuffing Supervision and Photograph charges {rate} per TEU','PER_TEU','TEU',145),
 (@cnf,'BL Charges','BL Charges','MANUAL',NULL,150),
 (@cnf,'BL Release at BLR','BL Release at BLR','MANUAL',NULL,155),
 (@cnf,'Post Shipment Document charges','Post Shipment Document charges','MANUAL',NULL,160);

-- Transport surcharge. Always 4000 per TEU across every bill it appears on.
INSERT INTO services (category_id, name, print_template, calculation_type, default_unit, sort_order) VALUES
 (@t,'Additional transportation via Hassan','Addnl. Charges for Transportation via Hassan','PER_TEU','TEU',200);

INSERT INTO services (category_id, name, print_template, calculation_type, default_unit, sort_order) VALUES
 (@ta,'Taxi hire for PQ inspection','Taxi hire paid for PQ inspection','MANUAL',NULL,300);

INSERT INTO services (category_id, name, print_template, calculation_type, default_unit, sort_order) VALUES
 (@s,'Expenses on Phytosanitary Certificate','EXPENSES ON PHYTOSANITARY CERTIFICATE {qty} SETS @RS{rate}/-','PER_SET','SET',400),
 (@s,'Service charges (PSC sets)','SERVICE CHARGES {qty} SETS@RS.{rate}/-','PER_SET','SET',410);

INSERT INTO services (category_id, name, print_template, calculation_type, default_unit, sort_order) VALUES
 (@cb,'Online application processing charges','{qty} Sets of applications through online processing charges @{rate}/set','PER_SET','SET',500),
 (@cb,'Documentation and scanning charges','{qty} sets of documentation and scanning charges@INR.{rate}/set','PER_SET','SET',510);

-- Rates as billed. Where a charge is flat per invoice the rate is the printed amount.
INSERT INTO service_rates (service_id, customer_id, rate, gst_rate, effective_from)
SELECT s.id, NULL, r.rate, 18.00, '2026-04-01'
FROM services s JOIN (
  SELECT 'Fumigation charges' n, 6000.00 rate UNION ALL
  SELECT 'Halting charges', 2000.00 UNION ALL
  SELECT 'VGM expenses', 750.00 UNION ALL
  SELECT 'LO/LO', 750.00 UNION ALL
  SELECT 'CHA Service charges', 2500.00 UNION ALL
  SELECT 'Customs Clearance charges', 1750.00 UNION ALL
  SELECT 'Port Expenses', 1250.00 UNION ALL
  SELECT 'JSW containers and Seal Scanning Charges', 800.00 UNION ALL
  SELECT 'Seal Charges', 1106.50 UNION ALL
  SELECT 'Dry Air Bags', 700.00 UNION ALL
  SELECT 'Pre shipment export documentation charges', 3000.00 UNION ALL
  SELECT 'Certificate of origin', 1750.00 UNION ALL
  SELECT 'EDI charges', 1000.00 UNION ALL
  SELECT 'Post Shipment Document charges', 500.00 UNION ALL
  SELECT 'Additional transportation via Hassan', 4000.00 UNION ALL
  SELECT 'Taxi hire for PQ inspection', 5000.00 UNION ALL
  SELECT 'Expenses on Phytosanitary Certificate', 2500.00 UNION ALL
  SELECT 'Service charges (PSC sets)', 500.00 UNION ALL
  SELECT 'Online application processing charges', 1250.00 UNION ALL
  SELECT 'Documentation and scanning charges', 500.00
) r ON r.n = s.name;

-- Per-container transport rates, verified against every T bill in both PDFs:
--   Kushalnagar 27000 x {1,2,3,4,6} = 27000/54000/81000/108000/162000
--   Somwarpet   29750 x {1,2,3}     = 29750/59500/89250
INSERT INTO transport_routes (name, origin, destination, print_template, rate_per_container) VALUES
 ('Mangalore-Kushalnagar','Mangalore','KUSHALNAGAR','Transportation Charges from Mangalore to KUSHALNAGAR and back',27000.00),
 ('Mangalore-Somwarpet','Mangalore','SOMWARPET','Transportation Charges from Mangalore to SOMWARPET and back',29750.00),
 ('Mangalore-Baikampady','Mangalore','BAIKAMPADY','Transportation Charges from Mangalore to BAIKAMPADY and back',12000.00),
 ('NMPA-Baikampady','NMPA','BAIKAMPADY','Transportation Charges from NMPA to BAIKAMPADY and back',12000.00);

-- password is 'changeme' (BCrypt). Change it on first login.
INSERT INTO users (username, password_hash, display_name, role) VALUES
 ('admin','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','Administrator','ADMIN');
