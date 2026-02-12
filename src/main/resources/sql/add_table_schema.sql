CREATE TABLE IF NOT EXISTS restaurant_table (
    id SERIAL CONSTRAINT restaurant_table_pk PRIMARY KEY,
    number INT NOT NULL UNIQUE
);

ALTER TABLE "order"
    ADD COLUMN id_table INT NOT NULL,
    ADD COLUMN arrival_datetime TIMESTAMP NOT NULL,
    ADD COLUMN departure_datetime TIMESTAMP NOT NULL;

ALTER TABLE "order"
    ADD CONSTRAINT fk_order_table
        FOREIGN KEY (id_table) REFERENCES restaurant_table(id);

INSERT INTO restaurant_table (id, number) VALUES
    (1, 1),
    (2, 2),
    (3, 3)
        ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('restaurant_table', 'id'), coalesce(max(id), 1)) FROM restaurant_table;