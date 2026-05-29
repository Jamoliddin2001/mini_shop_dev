-- V2: development seed data (categories + products).
-- The admin user is seeded in the security phase, where BCrypt hashing is available.
-- Idempotent inserts so re-running on a non-empty dev DB is safe.

INSERT INTO categories (name) VALUES
    ('Electronics'),
    ('Books'),
    ('Home & Kitchen'),
    ('Toys')
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (name, description, price, image_url, category_id)
SELECT v.name, v.description, v.price, v.image_url, c.id
FROM (VALUES
    ('Wireless Headphones', 'Over-ear Bluetooth headphones with ANC', 129.99, 'https://example.com/img/headphones.jpg', 'Electronics'),
    ('Mechanical Keyboard', 'Hot-swappable RGB mechanical keyboard',  89.50, 'https://example.com/img/keyboard.jpg',   'Electronics'),
    ('Clean Code',          'A handbook of agile software craftsmanship', 34.00, 'https://example.com/img/cleancode.jpg', 'Books'),
    ('The Pragmatic Programmer', 'Your journey to mastery',           42.00, 'https://example.com/img/pragprog.jpg',  'Books'),
    ('Ceramic Coffee Mug',  '350ml matte-finish ceramic mug',          12.90, 'https://example.com/img/mug.jpg',        'Home & Kitchen'),
    ('Building Blocks Set', '500-piece creative building blocks',      24.99, 'https://example.com/img/blocks.jpg',     'Toys')
) AS v(name, description, price, image_url, category_name)
JOIN categories c ON c.name = v.category_name
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name = v.name);
