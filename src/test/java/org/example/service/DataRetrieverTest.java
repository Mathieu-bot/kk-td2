package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.example.database.DBConnection;
import org.example.model.*;
import org.example.model.Order;
import org.junit.jupiter.api.*;

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

      stmt.execute("DELETE FROM dish_ingredient;");
      stmt.execute("DELETE FROM stock_movement;");
      stmt.execute("DELETE FROM ingredient;");
      stmt.execute("DELETE FROM dish;");

      stmt.execute("SELECT setval(pg_get_serial_sequence('dish', 'id'), 1, false);");
      stmt.execute("SELECT setval(pg_get_serial_sequence('ingredient', 'id'), 1, false);");
      stmt.execute("SELECT setval(pg_get_serial_sequence('dish_ingredient', 'id'), 1, false);");
      stmt.execute("SELECT setval(pg_get_serial_sequence('stock_movement', 'id'), 1, false);");

      String dataSql = Files.readString(Paths.get("src/main/resources/sql/data.sql"));

      for (String command : dataSql.split(";")) {
        String trimmed = command.trim();
        if (!trimmed.isEmpty() && !trimmed.toUpperCase().startsWith("SELECT SETVAL")) {
          stmt.execute(trimmed);
        }
      }

      String newSchemaSql = Files.readString(Paths.get("src/main/resources/sql/new_schema.sql"));

      for (String command : newSchemaSql.split(";")) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("INSERT ") || upper.startsWith("UPDATE ")) {
          stmt.execute(trimmed);
        }
      }

      String stockMovementSql =
          Files.readString(Paths.get("src/main/resources/sql/add_stock_movement_schema.sql"));

      for (String command : stockMovementSql.split(";")) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("INSERT ")) {
          stmt.execute(trimmed);
        }
      }

      stmt.execute(
          "SELECT setval(pg_get_serial_sequence('dish', 'id'), (SELECT COALESCE(MAX(id),0) + 1 FROM"
              + " dish), false);");
      stmt.execute(
          "SELECT setval(pg_get_serial_sequence('ingredient', 'id'), (SELECT COALESCE(MAX(id),0) +"
              + " 1 FROM ingredient), false);");
      stmt.execute(
          "SELECT setval(pg_get_serial_sequence('dish_ingredient', 'id'), (SELECT"
              + " COALESCE(MAX(id),0) + 1 FROM dish_ingredient), false);");
      stmt.execute(
          "SELECT setval(pg_get_serial_sequence('stock_movement', 'id'), (SELECT"
              + " COALESCE(MAX(id),0) + 1 FROM stock_movement), false);");
    }
  }

  @Test
  void testFindDishById_existing() {
    Dish dish = dataRetriever.findDishById(1);
    assertEquals("Salade fraiche", dish.getName());
    assertEquals(DishTypeEnum.START, dish.getDishType());
    assertEquals(2, dish.getDishIngredients().size());
    assertTrue(
        dish.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Laitue")));
    assertTrue(
        dish.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Tomate")));
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
    List<Ingredient> ingredients =
        dataRetriever.findIngredientsByCriteria(null, CategoryEnum.VEGETABLE, null, 1, 10);
    assertEquals(2, ingredients.size());
    assertTrue(ingredients.stream().allMatch(i -> i.getCategory() == CategoryEnum.VEGETABLE));
  }

  @Test
  void test7g_findIngredientsByCriteria_noResult() {
    List<Ingredient> ingredients =
        dataRetriever.findIngredientsByCriteria("Cho", null, "Beur", 1, 10);
    assertTrue(ingredients.isEmpty());
  }

  @Test
  void testFindIngredientsByCriteria_specific() {
    List<Ingredient> ingredients =
        dataRetriever.findIngredientsByCriteria("cho", null, "gateau", 1, 10);
    assertEquals(1, ingredients.size());
    assertEquals("Chocolat", ingredients.getFirst().getName());
  }

  @Test
  void testCreateIngredients_success() {
    Ingredient from = new Ingredient(0, "Fromage", 1200, CategoryEnum.DAIRY);
    Ingredient oignon = new Ingredient(0, "Oignon", 500, CategoryEnum.VEGETABLE);
    List<Ingredient> created = dataRetriever.createIngredients(List.of(from, oignon));
    assertEquals(2, created.size());
  }

  @Test
  void testCreateIngredients_duplicate() {
    Ingredient carotte = new Ingredient(0, "Carotte", 2000, CategoryEnum.VEGETABLE);
    Ingredient laitue = new Ingredient(0, "Laitue", 2000, CategoryEnum.VEGETABLE);
    assertThrows(
        RuntimeException.class, () -> dataRetriever.createIngredients(List.of(carotte, laitue)));
  }

  @Test
  void testSaveDish_newDish() {
    Dish newDish = new Dish(0, "Soupe de legumes", DishTypeEnum.START);
    Ingredient oignon = new Ingredient(0, "Oignon", 500.0, CategoryEnum.VEGETABLE);
    Ingredient fromage = new Ingredient(0, "Fromage", 1200.0, CategoryEnum.DAIRY);

    newDish.setDishIngredients(
        List.of(
            new DishIngredient(newDish, oignon, 1.0, Unit.KG),
            new DishIngredient(newDish, fromage, 1.0, Unit.KG)));

    Dish saved = dataRetriever.saveDish(newDish);

    assertTrue(saved.getId() > 0);
    assertEquals("Soupe de legumes", saved.getName());
    assertEquals(2, saved.getDishIngredients().size());
    assertTrue(
        saved.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Oignon")));
    assertTrue(
        saved.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Fromage")));
  }

  @Test
  void testSaveDish_updateExisting() {
    Dish salad = dataRetriever.findDishById(1);
    List<DishIngredient> updatedIngredients = new ArrayList<>(salad.getDishIngredients());
    updatedIngredients.add(
        new DishIngredient(
            salad, new Ingredient(0, "Oignon", 500.0, CategoryEnum.VEGETABLE), 1.0, Unit.KG));
    updatedIngredients.add(
        new DishIngredient(
            salad, new Ingredient(0, "Fromage", 1200.0, CategoryEnum.DAIRY), 1.0, Unit.KG));
    salad.setDishIngredients(updatedIngredients);

    Dish updated = dataRetriever.saveDish(salad);

    assertEquals(1, updated.getId());
    assertEquals("Salade fraiche", updated.getName());
    assertEquals(4, updated.getDishIngredients().size());
    assertTrue(
        updated.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Laitue")));
    assertTrue(
        updated.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Tomate")));
    assertTrue(
        updated.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Oignon")));
    assertTrue(
        updated.getDishIngredients().stream()
            .anyMatch(di -> di.getIngredient().getName().equals("Fromage")));
  }

  @Test
  void testGetDishCost_expectedValues() {
    Dish saladeFraiche = dataRetriever.findDishById(1);
    assertEquals(250.00, saladeFraiche.getDishCost(), 0.001);

    Dish pouletGrille = dataRetriever.findDishById(2);
    assertEquals(4500.00, pouletGrille.getDishCost(), 0.001);

    Dish rizAuxLegumes = dataRetriever.findDishById(3);
    assertEquals(0.00, rizAuxLegumes.getDishCost(), 0.001);

    Dish gateauChocolat = dataRetriever.findDishById(4);
    assertEquals(1400.00, gateauChocolat.getDishCost(), 0.001);

    Dish saladeFruits = dataRetriever.findDishById(5);
    assertEquals(0.00, saladeFruits.getDishCost(), 0.001);
  }

  @Test
  void testGetGrossMargin_expectedValues() {
    Dish saladeFraiche = dataRetriever.findDishById(1);
    assertEquals(3250.00, saladeFraiche.getGrossMargin(), 0.001);

    Dish pouletGrille = dataRetriever.findDishById(2);
    assertEquals(7500.00, pouletGrille.getGrossMargin(), 0.001);

    Dish rizAuxLegumes = dataRetriever.findDishById(3);
    assertThrows(IllegalStateException.class, rizAuxLegumes::getGrossMargin);

    Dish gateauChocolat = dataRetriever.findDishById(4);
    assertEquals(6600.00, gateauChocolat.getGrossMargin(), 0.001);

    Dish saladeFruits = dataRetriever.findDishById(5);
    assertThrows(IllegalStateException.class, saladeFruits::getGrossMargin);
  }

  @Test
  void testSaveOrder_andFindByReference_success() {
    Dish saladeFraiche = dataRetriever.findDishById(1);

    DishOrder line = new DishOrder(0, saladeFraiche, 1);
    Order order = new Order(0, null, Instant.parse("2024-01-06T12:00:00Z"), List.of(line));

    Order saved = dataRetriever.saveOrder(order);

    assertTrue(saved.getId() > 0);
    assertTrue(saved.getReference().matches("ORD\\d{5}"));
    assertEquals(1, saved.getDishOrders().size());
    assertEquals(1, saved.getDishOrders().getFirst().getDish().getId());
    assertEquals(1, saved.getDishOrders().getFirst().getQuantity());

    assertEquals(3500.00, saved.getTotalAmountWithoutVAT(), 0.001);
    assertEquals(4200.00, saved.getTotalAmountWithVAT(), 0.001);

    Order reloaded = dataRetriever.findOrderByReference(saved.getReference());
    assertEquals(saved.getId(), reloaded.getId());
    assertEquals(saved.getReference(), reloaded.getReference());
    assertEquals(1, reloaded.getDishOrders().size());
    assertEquals(1, reloaded.getDishOrders().getFirst().getDish().getId());
    assertEquals(1, reloaded.getDishOrders().getFirst().getQuantity());

    assertEquals(saved.getTotalAmountWithoutVAT(), reloaded.getTotalAmountWithoutVAT(), 0.001);
    assertEquals(saved.getTotalAmountWithVAT(), reloaded.getTotalAmountWithVAT(), 0.001);
  }

  @Test
  void testSaveOrder_notEnoughStock() {
    Dish pouletGrille = dataRetriever.findDishById(2);

    DishOrder line = new DishOrder(0, pouletGrille, 1000);
    Order order = new Order(0, "ORD00024", Instant.parse("2024-01-06T12:00:00Z"), List.of(line));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> dataRetriever.saveOrder(order));
    assertTrue(ex.getMessage().contains("Not enough stock for ingredient"));
  }

  @Test
  void testSaveOrder_consumesStock() throws SQLException {
    Instant t = Instant.parse("2024-01-06T12:00:00Z");

    Ingredient laitueBefore = dataRetriever.findIngredientById(1);
    Ingredient tomateBefore = dataRetriever.findIngredientById(2);

    double stockLaitueBefore = laitueBefore.getStockValueAt(t).getQuantity();
    double stockTomateBefore = tomateBefore.getStockValueAt(t).getQuantity();

    Dish saladeFraiche = dataRetriever.findDishById(1);
    DishOrder line = new DishOrder(0, saladeFraiche, 1);
    Order order = new Order(0, null, t, List.of(line));

    dataRetriever.saveOrder(order);

    Ingredient laitueAfter = dataRetriever.findIngredientById(1);
    Ingredient tomateAfter = dataRetriever.findIngredientById(2);

    double stockLaitueAfter = laitueAfter.getStockValueAt(t).getQuantity();
    double stockTomateAfter = tomateAfter.getStockValueAt(t).getQuantity();

    assertEquals(stockLaitueBefore - 0.20, stockLaitueAfter, 0.0001);
    assertEquals(stockTomateBefore - 0.15, stockTomateAfter, 0.0001);
  }

  @Test
  void testSaveOrder_invalidOrder_null() {
    assertThrows(IllegalArgumentException.class, () -> dataRetriever.saveOrder(null));
  }

  @Test
  void testSaveOrder_invalidOrder_emptyDishOrders() {
    Order order = new Order(0, "ORD00023", Instant.parse("2024-01-06T12:00:00Z"), List.of());

    assertThrows(IllegalArgumentException.class, () -> dataRetriever.saveOrder(order));
  }

  @Test
  void testGetStockValueAt_expectedValuesFromSqlData() throws SQLException {
    Instant t = Instant.parse("2024-01-06T12:00:00Z");

    Ingredient laitue = dataRetriever.findIngredientById(1);
    assertEquals(4.8, laitue.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient tomate = dataRetriever.findIngredientById(2);
    assertEquals(3.85, tomate.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient poulet = dataRetriever.findIngredientById(3);
    assertEquals(9.0, poulet.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient chocolat = dataRetriever.findIngredientById(4);
    assertEquals(2.7, chocolat.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient beurre = dataRetriever.findIngredientById(5);
    assertEquals(2.3, beurre.getStockValueAt(t).getQuantity(), 0.0001);
  }

  @Test
  void testGetStockValueAt_withMixedUnitsScenario() throws Exception {
    Instant t = Instant.parse("2024-01-06T12:00:00Z");

    try (Connection conn = dbConnection.getDBConnection();
        Statement stmt = conn.createStatement()) {

      stmt.execute("DELETE FROM stock_movement;");

      stmt.execute(
          "INSERT INTO stock_movement(id_ingredient, quantity, type, unit, creation_datetime) "
              + "VALUES (1, 5.0, 'IN', 'KG', '2024-01-05 08:00'),"
              + "(2, 4.0, 'IN', 'KG', '2024-01-05 08:00'),"
              + "(3, 10.0, 'IN', 'KG', '2024-01-05 08:00'),"
              + "(4, 3.0, 'IN', 'KG', '2024-01-05 08:00'),"
              + "(5, 2.5, 'IN', 'KG', '2024-01-05 08:00');");

      stmt.execute(
          "INSERT INTO stock_movement(id_ingredient, quantity, type, unit, creation_datetime) "
              + "VALUES (2, 5.0, 'OUT', 'PCS', '2024-01-06 12:00'),"
              + "(1, 2.0, 'OUT', 'PCS', '2024-01-06 12:00'),"
              + "(4, 1.0, 'OUT', 'L',   '2024-01-06 12:00'),"
              + "(3, 4.0, 'OUT', 'PCS', '2024-01-06 12:00'),"
              + "(5, 1.0, 'OUT', 'L',   '2024-01-06 12:00');");
    }

    Ingredient laitue = dataRetriever.findIngredientById(1);
    Ingredient tomate = dataRetriever.findIngredientById(2);
    Ingredient poulet = dataRetriever.findIngredientById(3);
    Ingredient chocolat = dataRetriever.findIngredientById(4);
    Ingredient beurre = dataRetriever.findIngredientById(5);

    assertEquals(4.0, laitue.getStockValueAt(t).getQuantity(), 0.0001);
    assertEquals(3.5, tomate.getStockValueAt(t).getQuantity(), 0.0001);
    assertEquals(9.5, poulet.getStockValueAt(t).getQuantity(), 0.0001);
    assertEquals(2.6, chocolat.getStockValueAt(t).getQuantity(), 0.0001);
    assertEquals(2.3, beurre.getStockValueAt(t).getQuantity(), 0.0001);
  }

}
