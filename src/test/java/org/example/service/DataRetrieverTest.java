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
  void testGetStockValueAt_expectedValuesFromSqlData() throws SQLException {
    Instant t = Instant.parse("2024-01-06T12:00:00Z");

    Ingredient laitue = loadIngredientWithMovements(1);
    assertEquals(4.8, laitue.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient tomate = loadIngredientWithMovements(2);
    assertEquals(3.85, tomate.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient poulet = loadIngredientWithMovements(3);
    assertEquals(9.0, poulet.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient chocolat = loadIngredientWithMovements(4);
    assertEquals(2.7, chocolat.getStockValueAt(t).getQuantity(), 0.0001);

    Ingredient beurre = loadIngredientWithMovements(5);
    assertEquals(2.3, beurre.getStockValueAt(t).getQuantity(), 0.0001);
  }

  private Ingredient loadIngredientWithMovements(int ingredientId) throws SQLException {
    try (Connection conn = dbConnection.getDBConnection()) {

      Ingredient ingredient;
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT id, name, price, category FROM ingredient WHERE id = ?")) {
        ps.setInt(1, ingredientId);
        try (ResultSet rs = ps.executeQuery()) {
          assertTrue(rs.next(), "Ingredient not found (id=" + ingredientId + ")");
          ingredient =
              new Ingredient(
                  rs.getInt("id"),
                  rs.getString("name"),
                  rs.getDouble("price"),
                  CategoryEnum.valueOf(rs.getString("category")));
        }
      }

      List<StockMovement> movements = new ArrayList<>();
      try (PreparedStatement ps =
          conn.prepareStatement(
              "SELECT id, quantity, type, unit, creation_datetime FROM stock_movement WHERE"
                  + " id_ingredient = ?")) {
        ps.setInt(1, ingredientId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            StockValue value =
                new StockValue(rs.getDouble("quantity"), Unit.valueOf(rs.getString("unit")));
            StockMovement movement =
                new StockMovement(
                    rs.getInt("id"),
                    value,
                    MovementTypeEnum.valueOf(rs.getString("type")),
                    rs.getTimestamp("creation_datetime").toInstant());
            movements.add(movement);
          }
        }
      }

      ingredient.setStockMovementList(movements);
      return ingredient;
    }
  }
}
