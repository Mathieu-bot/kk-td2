INSERT INTO dish (id, name, dish_type) VALUES
    (1, 'Salade fraiche', 'START'),
    (2, 'Poulet grille', 'MAIN'),
    (3, 'Riz aux legumes', 'MAIN'),
    (4, 'Gateau au chocolat', 'DESSERT'),
    (5, 'Salade de fruits', 'DESSERT');

SELECT setval(
   pg_get_serial_sequence('dish', 'id'),
   (SELECT MAX(id) FROM dish)
);

INSERT INTO ingredient (id, name, price, category) VALUES
    (1, 'Laitue', 800.00, 'VEGETABLE'),
    (2, 'Tomate', 600.00, 'VEGETABLE'),
    (3, 'Poulet', 4500.00, 'ANIMAL'),
    (4, 'Chocolat', 3000.00, 'OTHER'),
    (5, 'Beurre', 2500.00, 'DAIRY');

SELECT setval(
   pg_get_serial_sequence('ingredient', 'id'),
   (SELECT MAX(id) FROM ingredient)
);
