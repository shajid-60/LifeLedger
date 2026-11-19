-- Reference data every environment needs (dev, test, prod).
-- Demo users, shops and products live in M3's demo seed, not here.

INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('SELLER'),
    ('BUYER'),
    ('REPAIR_SHOP');

-- validation_strategy = Spring bean name of the ProductValidationStrategy (Strategy pattern)
INSERT INTO product_categories (code, name, validation_strategy) VALUES
    ('LAPTOP', 'Laptop', 'laptopValidationStrategy'),
    ('DESKTOP', 'Desktop', 'desktopValidationStrategy');
