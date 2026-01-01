package org.example.service;

import org.example.database.DBConnection;
import org.example.model.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRetrieverTest {

    private DataRetriever dataRetriever;
    private DBConnection dbConnection;

    @BeforeAll
    void init() {
        dbConnection = new DBConnection();
        dataRetriever = new DataRetriever(dbConnection);
    }

    @BeforeEach
    void resetDatabaseTables() throws SQLException, IOException {
        try (Connection conn = dbConnection.getDBConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DELETE FROM ingredient;");
            stmt.execute("DELETE FROM dish;");

            stmt.execute("SELECT setval(pg_get_serial_sequence('dish', 'id'), 1, false);");
            stmt.execute("SELECT setval(pg_get_serial_sequence('ingredient', 'id'), 1, false);");

            String dataSql = Files.readString(Paths.get("src/main/resources/sql/data.sql"));

            for (String command : dataSql.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty() && !trimmed.toUpperCase().startsWith("SELECT SETVAL")) {
                    stmt.execute(trimmed);
                }
            }

            stmt.execute("SELECT setval(pg_get_serial_sequence('dish', 'id'), (SELECT COALESCE(MAX(id),0) + 1 FROM dish), false);");
            stmt.execute("SELECT setval(pg_get_serial_sequence('ingredient', 'id'), (SELECT COALESCE(MAX(id),0) + 1 FROM ingredient), false);");
        }
    }

    @Test
    void testFindDishById_existing() {
        Dish dish = dataRetriever.findDishById(1);
        assertEquals("Salade fraiche", dish.getName());
        assertEquals(DishTypeEnum.START, dish.getDishType());
        assertEquals(2, dish.getIngredients().size());
        assertTrue(dish.getIngredients().stream().anyMatch(i -> i.getName().equals("Laitue")));
        assertTrue(dish.getIngredients().stream().anyMatch(i -> i.getName().equals("Tomate")));
    }

    @Test
    void testFindDishById_notExisting() {
        assertThrows(RuntimeException.class, () -> dataRetriever.findDishById(999));
    }

    @Test
    void testFindIngredients_page2_size2() {
        List<Ingredient> ingredients = dataRetriever.findIngredients(2, 2);
        assertEquals(2, ingredients.size());
        assertEquals("Poulet", ingredients.get(0).getName());
        assertEquals("Chocolat", ingredients.get(1).getName());
    }

    @Test
    void testFindIngredients_emptyPage() {
        List<Ingredient> ingredients = dataRetriever.findIngredients(3, 5);
        assertTrue(ingredients.isEmpty());
    }

    @Test
    void testFindDishByIngredientName() {
        List<Dish> dishes = dataRetriever.findDishByIngredientName("eur");
        assertEquals(1, dishes.size());
        assertEquals("Gateau au chocolat", dishes.getFirst().getName());
    }

    @Test
    void testFindIngredientsByCriteria_categoryOnly() {
        List<Ingredient> ingredients = dataRetriever.findIngredientsByCriteria(
                null, CategoryEnum.VEGETABLE, null, 1, 10
        );
        assertEquals(2, ingredients.size());
        assertTrue(ingredients.stream().allMatch(i -> i.getCategory() == CategoryEnum.VEGETABLE));
    }

    @Test
    void test7g_findIngredientsByCriteria_noResult() {
        List<Ingredient> ingredients = dataRetriever.findIngredientsByCriteria(
                "Cho", null, "Beur", 1, 10);
        assertTrue(ingredients.isEmpty());
    }

    @Test
    void testFindIngredientsByCriteria_specific() {
        List<Ingredient> ingredients = dataRetriever.findIngredientsByCriteria(
                "cho", null, "gateau", 1, 10
        );
        assertEquals(1, ingredients.size());
        assertEquals("Chocolat", ingredients.getFirst().getName());
    }

    @Test
    void testCreateIngredients_success() {
        Ingredient from = new Ingredient(0, "Fromage", 1200, CategoryEnum.DAIRY, null);
        Ingredient oignon = new Ingredient(0, "Oignon", 500, CategoryEnum.VEGETABLE, null);
        List<Ingredient> created = dataRetriever.createIngredients(List.of(from, oignon));
        assertEquals(2, created.size());
    }

    @Test
    void testCreateIngredients_duplicate() {
        Ingredient carotte = new Ingredient(0, "Carotte", 2000, CategoryEnum.VEGETABLE, null);
        Ingredient laitue = new Ingredient(0, "Laitue", 2000, CategoryEnum.VEGETABLE, null);
        assertThrows(RuntimeException.class, () -> dataRetriever.createIngredients(List.of(carotte, laitue)));
    }

    @Test
    void testSaveDish_newDish() {
        Dish newDish = new Dish(0, "Soupe de legumes", DishTypeEnum.START);
        newDish.addIngredient(new Ingredient(0, "Oignon", 500.0, CategoryEnum.VEGETABLE, null));
        newDish.addIngredient(new Ingredient(0, "Fromage", 1200.0, CategoryEnum.DAIRY, null));

        Dish saved = dataRetriever.saveDish(newDish);

        assertTrue(saved.getId() > 0);
        assertEquals("Soupe de legumes", saved.getName());
        assertEquals(2, saved.getIngredients().size());
        assertTrue(saved.getIngredients().stream().anyMatch(i -> i.getName().equals("Oignon")));
        assertTrue(saved.getIngredients().stream().anyMatch(i -> i.getName().equals("Fromage")));
    }

}