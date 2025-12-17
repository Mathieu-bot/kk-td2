-- Types ENUM
CREATE TYPE ingredient_category AS ENUM (
    'VEGETABLE',
    'ANIMAL',
    'MARINE',
    'DAIRY',
    'OTHER'
);

CREATE TYPE dish_type AS ENUM (
    'START',
    'MAIN',
    'DESSERT'
);

CREATE TABLE ingredient (
    id SERIAL CONSTRAINT ingredient_pk PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    category ingredient_category NOT NULL,
    id_dish INT,
    CONSTRAINT fk_dish
        FOREIGN KEY (id_dish)
            REFERENCES dish(id)
            ON DELETE SET NULL
);


CREATE TABLE dish (
    id SERIAL CONSTRAINT dish_pk PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    dish_type dish_type NOT NULL
);

