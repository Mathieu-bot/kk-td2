ALTER TABLE dish
    ADD COLUMN IF NOT EXISTS price NUMERIC(10,2);

UPDATE dish SET price = 2000 WHERE name = 'Salade fraiche';
UPDATE dish SET price = 6000 WHERE name = 'Poulet grille';
UPDATE dish SET price = NULL WHERE name IN (
        'Riz aux legumes',
        'Gateau au chocolat',
        'Salade de fruits'
    );
