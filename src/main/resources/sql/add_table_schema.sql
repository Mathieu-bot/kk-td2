CREATE TABLE IF NOT EXISTS restaurant_table (
    id SERIAL CONSTRAINT restaurant_table_pk PRIMARY KEY,
    number INT NOT NULL UNIQUE
);

ALTER TABLE "order"
    ADD COLUMN IF NOT EXISTS id_table INT NOT NULL,
    ADD COLUMN IF NOT EXISTS arrival_datetime TIMESTAMP NOT NULL,
    ADD COLUMN IF NOT EXISTS departure_datetime TIMESTAMP NOT NULL;

ALTER TABLE "order"
    ADD CONSTRAINT IF NOT EXISTS fk_order_table
        FOREIGN KEY (id_table) REFERENCES restaurant_table(id);

INSERT INTO restaurant_table (id, number) VALUES
    (1, 1),
    (2, 2),
    (3, 3)
ON CONFLICT DO NOTHING;
