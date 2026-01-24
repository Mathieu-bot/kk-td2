CREATE TABLE "order" (
    id SERIAL CONSTRAINT order_pk PRIMARY KEY,
    reference VARCHAR(255) NOT NULL,
    creation_datetime TIMESTAMP NOT NULL
);

CREATE SEQUENCE order_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE dish_order (
    id SERIAL CONSTRAINT dish_order_pk PRIMARY KEY,
    id_order INT NOT NULL,
    id_dish INT NOT NULL,
    quantity NUMERIC(10,2) NOT NULL,
    CONSTRAINT fk_order FOREIGN KEY (id_order) REFERENCES "order"(id) ON DELETE CASCADE,
    CONSTRAINT fk_dish FOREIGN KEY (id_dish) REFERENCES dish(id) ON DELETE CASCADE
);