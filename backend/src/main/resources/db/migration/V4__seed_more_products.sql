-- V4: richer development seed.
-- Adds more categories + ~60 products, and repairs the broken example.com image
-- URLs from V2 (those hosts don't exist, so no photo ever rendered).
--
-- Why a new migration (not editing V2): V2 is already applied and recorded in
-- flyway_schema_history; editing it would change its checksum and fail validation
-- on the next startup. Migrations are immutable once shipped.
--
-- Images use LoremFlickr (real CC photos by keyword). The `lock` query param pins
-- a specific photo per product so the image is stable across reloads instead of
-- changing on every request.
--
-- Idempotent: ON CONFLICT / WHERE NOT EXISTS make a re-run on a non-empty dev DB safe.

-- ---------------------------------------------------------------------------
-- New categories
-- ---------------------------------------------------------------------------
INSERT INTO categories (name) VALUES
    ('Sports & Outdoors'),
    ('Clothing'),
    ('Beauty'),
    ('Office')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Repair the 6 original products' broken images (example.com -> LoremFlickr).
-- ---------------------------------------------------------------------------
UPDATE products SET image_url = 'https://loremflickr.com/600/400/' || kw || '?lock=' || lock_id
FROM (VALUES
    ('Wireless Headphones',      'headphones',       1),
    ('Mechanical Keyboard',      'keyboard',         2),
    ('Clean Code',               'book,programming', 3),
    ('The Pragmatic Programmer', 'book',             4),
    ('Ceramic Coffee Mug',       'coffee,mug',       5),
    ('Building Blocks Set',      'toy,blocks',       6)
) AS fix(name, kw, lock_id)
WHERE products.name = fix.name;

-- ---------------------------------------------------------------------------
-- New products. image_url is built as:
--   https://loremflickr.com/600/400/<keyword>?lock=<stable id>
-- ---------------------------------------------------------------------------
INSERT INTO products (name, description, price, image_url, category_id)
SELECT v.name, v.description, v.price,
       'https://loremflickr.com/600/400/' || v.img_kw || '?lock=' || v.lock_id,
       c.id
FROM (VALUES
    -- Electronics (12)
    ('USB-C Fast Charger 65W',     'GaN compact charger with 3 ports',            29.99, 'charger,usb',        101, 'Electronics'),
    ('4K Action Camera',           'Waterproof action cam with stabilization',   199.00, 'camera,action',      102, 'Electronics'),
    ('Bluetooth Speaker',          'Portable speaker, 12h battery, IPX7',         59.90, 'speaker,bluetooth',  103, 'Electronics'),
    ('Wireless Mouse',             'Silent ergonomic mouse, 2.4G + BT',           24.50, 'mouse,computer',     104, 'Electronics'),
    ('27-inch 4K Monitor',         'IPS UHD monitor with USB-C hub',             329.00, 'monitor,desktop',    105, 'Electronics'),
    ('Smartwatch Series 7',        'AMOLED fitness smartwatch, GPS',             179.99, 'smartwatch,watch',   106, 'Electronics'),
    ('Portable SSD 1TB',           'USB 3.2 external SSD, 1050MB/s',             109.00, 'ssd,storage',        107, 'Electronics'),
    ('Noise-Cancelling Earbuds',   'True wireless earbuds with ANC',              89.00, 'earbuds,audio',      108, 'Electronics'),
    ('Webcam 1080p',               'Full-HD webcam with privacy shutter',         39.90, 'webcam,camera',      109, 'Electronics'),
    ('Power Bank 20000mAh',        'Fast-charge power bank, dual USB-C',          45.00, 'powerbank,battery',  110, 'Electronics'),
    ('Gaming Headset',             'Surround gaming headset with mic',            69.99, 'headset,gaming',     111, 'Electronics'),
    ('LED Strip Lights 5m',        'RGB smart LED strip, app controlled',         19.99, 'led,lights',         112, 'Electronics'),

    -- Books (8)
    ('Refactoring',                       'Improving the design of existing code', 47.00, 'book',         113, 'Books'),
    ('Design Patterns (GoF)',             'Elements of reusable OO software',       54.00, 'book,library', 114, 'Books'),
    ('Effective Java',                    'Best practices for the Java platform',   45.00, 'book,java',    115, 'Books'),
    ('Domain-Driven Design',              'Tackling complexity in software',        52.00, 'book',         116, 'Books'),
    ('You Don''t Know JS Yet',            'Deep dive into JavaScript',              28.00, 'book,reading', 117, 'Books'),
    ('The Mythical Man-Month',            'Essays on software engineering',         33.00, 'book',         118, 'Books'),
    ('Working Effectively with Legacy Code','Strategies for legacy systems',        44.00, 'book,library', 119, 'Books'),
    ('Atomic Habits',                     'Tiny changes, remarkable results',       21.00, 'book,reading', 120, 'Books'),

    -- Home & Kitchen (10)
    ('Stainless French Press',     '1L double-wall coffee press',                 34.90, 'coffee,press',    121, 'Home & Kitchen'),
    ('Cast Iron Skillet',          'Pre-seasoned 26cm cast iron pan',             39.00, 'skillet,pan',     122, 'Home & Kitchen'),
    ('Electric Kettle',            '1.7L fast-boil kettle, auto shut-off',        29.50, 'kettle,kitchen',  123, 'Home & Kitchen'),
    ('Chef Knife 8-inch',          'High-carbon stainless steel chef knife',      49.00, 'knife,kitchen',   124, 'Home & Kitchen'),
    ('Bamboo Cutting Board',       'Large eco bamboo board with groove',          18.90, 'cutting,board',   125, 'Home & Kitchen'),
    ('Glass Storage Set',          '10-piece airtight glass containers',          32.00, 'container,kitchen',126,'Home & Kitchen'),
    ('Non-stick Frying Pan',       '28cm induction-ready non-stick pan',          27.90, 'pan,cooking',     127, 'Home & Kitchen'),
    ('Espresso Machine',           '15-bar semi-auto espresso maker',            159.00, 'espresso,coffee', 128, 'Home & Kitchen'),
    ('Toaster 2-slice',            'Wide-slot stainless toaster',                 35.00, 'toaster,kitchen', 129, 'Home & Kitchen'),
    ('Blender 1000W',              'High-speed blender with glass jug',           64.00, 'blender,kitchen', 130, 'Home & Kitchen'),

    -- Toys (6)
    ('Remote Control Car',         '1:18 RC off-road car, rechargeable',          42.00, 'toy,car',         131, 'Toys'),
    ('Wooden Train Set',           '40-piece wooden railway set',                 36.50, 'toy,train',       132, 'Toys'),
    ('Plush Teddy Bear',           'Soft 40cm plush teddy bear',                  19.90, 'teddy,bear',      133, 'Toys'),
    ('Jigsaw Puzzle 1000pc',       'Landscape 1000-piece jigsaw puzzle',          14.99, 'puzzle',          134, 'Toys'),
    ('Board Game Classic',         'Family strategy board game',                  29.99, 'boardgame,game',  135, 'Toys'),
    ('Mini Drone',                 'Beginner quadcopter with camera',             58.00, 'drone',           136, 'Toys'),

    -- Sports & Outdoors (8)
    ('Yoga Mat',                   'Non-slip 6mm exercise yoga mat',              22.00, 'yoga,mat',        137, 'Sports & Outdoors'),
    ('Dumbbell Set 20kg',          'Adjustable dumbbell pair',                    79.00, 'dumbbell,fitness',138, 'Sports & Outdoors'),
    ('Running Shoes',              'Lightweight breathable running shoes',        89.90, 'running,shoes',   139, 'Sports & Outdoors'),
    ('Camping Tent 2-Person',      'Waterproof dome tent, easy setup',           109.00, 'tent,camping',    140, 'Sports & Outdoors'),
    ('Stainless Water Bottle',     'Insulated 750ml bottle, 24h cold',            24.90, 'bottle,water',    141, 'Sports & Outdoors'),
    ('Bike Helmet',                'Lightweight ventilated cycling helmet',       49.99, 'helmet,bike',     142, 'Sports & Outdoors'),
    ('Resistance Bands Set',       '5-level workout resistance bands',            17.50, 'fitness,gym',     143, 'Sports & Outdoors'),
    ('Trekking Backpack 40L',      'Hiking backpack with rain cover',             74.00, 'backpack,hiking', 144, 'Sports & Outdoors'),

    -- Clothing (6)
    ('Cotton T-Shirt',             'Soft 100% cotton crew-neck tee',              15.90, 'tshirt,clothing', 145, 'Clothing'),
    ('Denim Jacket',               'Classic mid-wash denim jacket',               59.00, 'denim,jacket',    146, 'Clothing'),
    ('Wool Beanie',                'Warm ribbed wool beanie hat',                 16.50, 'beanie,hat',      147, 'Clothing'),
    ('Leather Belt',               'Genuine leather belt with metal buckle',      29.00, 'belt,leather',    148, 'Clothing'),
    ('Running Socks 3-Pack',       'Breathable cushioned ankle socks',            12.90, 'socks',           149, 'Clothing'),
    ('Hooded Sweatshirt',          'Fleece-lined pullover hoodie',                39.90, 'hoodie,clothing', 150, 'Clothing'),

    -- Beauty (6)
    ('Vitamin C Serum',            'Brightening facial serum, 30ml',              26.00, 'cosmetics,serum', 151, 'Beauty'),
    ('Moisturizing Cream',         'Daily hydrating face cream',                  22.50, 'skincare,cream',  152, 'Beauty'),
    ('Bamboo Toothbrush Set',      'Eco 4-pack bamboo toothbrushes',               9.90, 'toothbrush',      153, 'Beauty'),
    ('Natural Shampoo',            'Sulfate-free nourishing shampoo',             14.00, 'shampoo,beauty',  154, 'Beauty'),
    ('Lip Balm Trio',             'Set of 3 moisturizing lip balms',              11.50, 'cosmetics,lips',  155, 'Beauty'),
    ('Face Mask Set',              'Hydrating sheet mask 5-pack',                 18.00, 'spa,mask',        156, 'Beauty'),

    -- Office (6)
    ('Ergonomic Office Chair',     'Mesh back chair with lumbar support',        149.00, 'office,chair',    157, 'Office'),
    ('Standing Desk Converter',    'Height-adjustable desktop riser',            119.00, 'desk,office',     158, 'Office'),
    ('LED Desk Lamp',              'Dimmable lamp with USB charging',             34.90, 'lamp,desk',       159, 'Office'),
    ('Notebook A5 Hardcover',      'Dotted hardcover notebook, 200 pages',         9.50, 'notebook',        160, 'Office'),
    ('Fountain Pen',               'Fine-nib refillable fountain pen',            27.00, 'pen,writing',     161, 'Office'),
    ('Desk Organizer',            'Wooden multi-slot desk organizer',             21.90, 'desk,organizer',  162, 'Office')
) AS v(name, description, price, img_kw, lock_id, category_name)
JOIN categories c ON c.name = v.category_name
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name = v.name);
