INSERT INTO dish (id, name, dish_type) VALUES
    (1, 'Salade fraiche', 'START'),
    (2, 'Poulet grille', 'MAIN'),
    (3, 'Riz aux legumes', 'MAIN'),
    (4, 'Geteau au chocolat', 'DESSERT'),
    (5, 'Salade de fruits', 'DESSERT');

SELECT setval(
   pg_get_serial_sequence('dish', 'id'),
   (SELECT MAX(id) FROM dish)
);

INSERT INTO ingredient (id, name, price, category, id_dish) VALUES
    (1, 'Laitue', 800.00, 'VEGETABLE', 1),
    (2, 'Tomate', 600.00, 'VEGETABLE', 1),
    (3, 'Poulet', 4500.00, 'ANIMAL', 2),
    (4, 'Chocolat', 3000.00, 'OTHER', 4),
    (5, 'Beurre', 2500.00, 'DAIRY', 4);

SELECT setval(
   pg_get_serial_sequence('ingredient', 'id'),
   (SELECT MAX(id) FROM ingredient)
);
