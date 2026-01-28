package org.example.service;

import static java.sql.Types.INTEGER;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.database.DBConnection;
import org.example.model.*;

public class DataRetriever {

  private final DBConnection dbConnection;

  public DataRetriever(DBConnection dbConnection) {
    this.dbConnection = dbConnection;
  }

  public Dish findDishById(int id) {
    String sql = "SELECT id, name, dish_type, price FROM dish WHERE id = ?";
    Connection connection = dbConnection.getDBConnection();

    try {
      PreparedStatement ps = connection.prepareStatement(sql);
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();

      if (rs.next()) {
        Dish dish = mapDish(rs, "id", "name", "price");

        dish.setDishIngredients(findDishIngredientsByDishId(dish.getId()));
        return dish;
      }
      throw new RuntimeException("Dish not found (id=" + id + ")");
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(connection);
    }
  }

  public List<DishIngredient> findDishIngredientsByDishId(int dishId) {
    List<DishIngredient> result = new ArrayList<>();
    String sql =
        """
            SELECT di.id_dish,
                   di.id_ingredient,
                   di.quantity_required,
                   di.unit,
                   d.id AS dish_id,
                   d.name AS dish_name,
                   d.dish_type,
                   d.price AS dish_price,
                   i.id AS ingredient_id,
                   i.name AS ingredient_name,
                   i.price AS ingredient_price,
                   i.category
            FROM dish_ingredient di
            JOIN dish d ON d.id = di.id_dish
            JOIN ingredient i ON i.id = di.id_ingredient
            WHERE di.id_dish = ?
        """;
    Connection connection = dbConnection.getDBConnection();

    try {
      PreparedStatement ps = connection.prepareStatement(sql);
      ps.setInt(1, dishId);
      ResultSet rs = ps.executeQuery();

      while (rs.next()) {
        Dish dish = mapDish(rs, "dish_id", "dish_name", "dish_price");

        Ingredient ingredient =
            mapIngredient(rs, "ingredient_id", "ingredient_name", "ingredient_price");

        double quantity = rs.getDouble("quantity_required");
        Unit unit = Unit.valueOf(rs.getString("unit"));

        result.add(new DishIngredient(dish, ingredient, quantity, unit));
      }
      return result;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(connection);
    }
  }

  public List<Ingredient> findIngredients(int page, int size) {
    int offset = (page - 1) * size;
    String sql =
        """
    SELECT i.id AS ingredient_id, i.name AS ingredient_name, i.price as ingredient_price, i.category,
           d.id AS dish_id, d.name AS dish_name, d.dish_type, d.price as dish_price,
           di.quantity_required
    FROM dish_ingredient di
    JOIN ingredient i ON i.id = di.id_ingredient
    JOIN dish d ON d.id = di.id_dish
    ORDER BY i.id, d.id
    LIMIT ? OFFSET ?
""";

    Connection connection = dbConnection.getDBConnection();

    try {
      PreparedStatement ps = connection.prepareStatement(sql);
      ps.setInt(1, size);
      ps.setInt(2, offset);

      ResultSet rs = ps.executeQuery();
      return mapIngredientsWithOptionalQuantity(rs);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(connection);
    }
  }

  public List<Ingredient> createIngredients(List<Ingredient> newIngredients) {
    checkDuplicatesInList(newIngredients);
    Connection conn = dbConnection.getDBConnection();

    try {
      conn.setAutoCommit(false);
      for (Ingredient ingredient : newIngredients) {
        if (ingredientExists(conn, ingredient.getName())) {
          throw new RuntimeException(
              "Ingredient already exists in database: " + ingredient.getName());
        }
        String sql =
            "INSERT INTO ingredient(name, category, price) VALUES (?, ?::ingredient_category, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
          ps.setString(1, ingredient.getName());
          ps.setString(2, ingredient.getCategory().name());
          ps.setDouble(3, ingredient.getPrice());
          ps.executeUpdate();
        }
      }

      conn.commit();
      return newIngredients;

    } catch (SQLException e) {
      try {
        conn.rollback();
      } catch (SQLException ex) {
        throw new RuntimeException("Rollback failed", ex);
      }
      throw new RuntimeException("Transaction failed: " + e.getMessage(), e);
    } finally {
      try {
        conn.setAutoCommit(true);
      } catch (SQLException ignored) {
      }
      dbConnection.close(conn);
    }
  }

  public Ingredient saveIngredient(Ingredient toSave) {
    Connection conn = dbConnection.getDBConnection();

    try {
      boolean isUpdate = toSave.getId() > 0;

      if (!isUpdate && ingredientExists(conn, toSave.getName())) {
        throw new RuntimeException("Ingredient already exists in database: " + toSave.getName());
      }

      Ingredient savedIngredient = upsertIngredient(conn, toSave);

      if (toSave.getStockMovementList() != null && !toSave.getStockMovementList().isEmpty()) {
        saveStockMovements(conn, savedIngredient.getId(), toSave.getStockMovementList());
      }

      return savedIngredient;

    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(conn);
    }
  }

  public Dish saveDish(Dish dishToSave) {

    String upsertDishSql =
        """
            INSERT INTO dish(id, name, dish_type, price)
            VALUES (?, ?, ?::dish_type, ?) ON CONFLICT (id) DO UPDATE
            SET name = excluded.name, dish_type = excluded.dish_type, price = excluded.price
            RETURNING id, name, dish_type, price
        """;

    Connection conn = dbConnection.getDBConnection();
    int dishId;

    try {

      try (PreparedStatement ps = conn.prepareStatement(upsertDishSql)) {
        Integer idParam =
            dishToSave.getId() > 0
                ? dishToSave.getId()
                : getNextId(conn, "dish", "Unable to generate new id for dish");
        ps.setObject(1, idParam, INTEGER);
        ps.setString(2, dishToSave.getName());
        ps.setString(3, dishToSave.getDishType().name());
        ps.setObject(4, dishToSave.getPrice());

        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
          dishId = rs.getInt("id");
        } else {
          throw new RuntimeException("Dish not found (id=" + dishToSave.getId() + ")");
        }
      }

      try (PreparedStatement ps =
          conn.prepareStatement("DELETE FROM dish_ingredient WHERE id_dish = ?")) {
        ps.setInt(1, dishId);
        ps.executeUpdate();
      }

      String insertDishIngredientSql =
          "INSERT INTO dish_ingredient(id_dish, id_ingredient, quantity_required, unit) VALUES (?,"
              + " ?, ?, ?::unit_type)";
      try (PreparedStatement ps = conn.prepareStatement(insertDishIngredientSql)) {
        for (DishIngredient di : dishToSave.getDishIngredients()) {
          Ingredient ing = di.getIngredient();
          int ingredientId = findOrCreateIngredient(conn, ing);
          double quantity = di.getQuantity();
          Unit unit = di.getUnit();

          ps.setInt(1, dishId);
          ps.setInt(2, ingredientId);
          ps.setDouble(3, quantity);
          ps.setString(4, unit.name());
          ps.addBatch();
        }
        ps.executeBatch();
      }

      return findDishById(dishId);

    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(conn);
    }
  }

  public List<Dish> findDishByIngredientName(String ingredientName) {
    List<Dish> dishes = new ArrayList<>();
    String sql =
        """
            SELECT DISTINCT d.id, d.name, d.dish_type, d.price
            FROM dish d
            JOIN dish_ingredient di ON di.id_dish = d.id
            JOIN ingredient i ON i.id = di.id_ingredient
            WHERE i.name ILIKE ?
        """;
    Connection connection = dbConnection.getDBConnection();

    try {
      PreparedStatement ps = connection.prepareStatement(sql);
      ps.setString(1, "%" + ingredientName + "%");
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        Dish dish = mapDish(rs, "id", "name", "price");
        dishes.add(dish);
      }

      return dishes;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(connection);
    }
  }

  public List<Ingredient> findIngredientsByCriteria(
      String ingredientName, CategoryEnum category, String dishName, int page, int size) {
    int offset = (page - 1) * size;
    StringBuilder sql =
        new StringBuilder(
            """
    SELECT i.id AS ingredient_id, i.name AS ingredient_name, i.price as ingredient_price, i.category,
           d.id AS dish_id, d.name AS dish_name, d.dish_type, d.price as dish_price,
           di.quantity_required
    FROM dish_ingredient di
    JOIN ingredient i ON i.id = di.id_ingredient
    JOIN dish d ON di.id_dish = d.id
    WHERE 1=1
""");

    List<Object> params = new ArrayList<>();

    if (ingredientName != null) {
      sql.append(" AND i.name ILIKE ?");
      params.add("%" + ingredientName + "%");
    }

    if (category != null) {
      sql.append(" AND i.category = ?::ingredient_category");
      params.add(category.name());
    }

    if (dishName != null) {
      sql.append(" AND d.name ILIKE ?");
      params.add("%" + dishName + "%");
    }

    sql.append(" LIMIT ? OFFSET ?");
    params.add(size);
    params.add(offset);

    Connection connection = dbConnection.getDBConnection();

    try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
      for (int i = 0; i < params.size(); i++) {
        Object param = params.get(i);
        if (param instanceof String) {
          ps.setString(i + 1, (String) param);
        } else if (param instanceof Integer) {
          ps.setInt(i + 1, (Integer) param);
        }
      }

      ResultSet rs = ps.executeQuery();
      return mapIngredientsWithOptionalQuantity(rs);

    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(connection);
    }
  }

  public Order saveOrder(Order orderToSave) {
    List<DishOrder> dishOrders = validateOrder(orderToSave);

    Connection conn = dbConnection.getDBConnection();

    try {
      conn.setAutoCommit(false);

      Instant checkInstant =
          orderToSave.getCreationDateTime() != null
              ? orderToSave.getCreationDateTime()
              : Instant.now();

      boolean isUpdate = orderToSave.getId() > 0;

      Map<Integer, Integer> newDishQuantities = aggregateDishQuantities(dishOrders);
      Map<Integer, Double> newRequiredQuantities =
          computeRequiredQuantities(conn, newDishQuantities);

      Map<Integer, Double> existingRequiredQuantities = new HashMap<>();
      if (isUpdate) {
        Map<Integer, Integer> existingDishQuantities =
            loadExistingDishQuantitiesForOrder(conn, orderToSave.getId());
        if (!existingDishQuantities.isEmpty()) {
          existingRequiredQuantities = computeRequiredQuantities(conn, existingDishQuantities);
        }
      }

      Map<Integer, Double> deltaQuantities = new HashMap<>(newRequiredQuantities);
      for (Map.Entry<Integer, Double> entry : existingRequiredQuantities.entrySet()) {
        deltaQuantities.merge(entry.getKey(), -entry.getValue(), Double::sum);
      }

      Map<Integer, Double> additionalRequired = new HashMap<>();
      for (Map.Entry<Integer, Double> entry : deltaQuantities.entrySet()) {
        if (entry.getValue() > 0) {
          additionalRequired.put(entry.getKey(), entry.getValue());
        }
      }

      checkStockOrThrow(conn, additionalRequired, checkInstant);

      Order savedOrder = upsertOrderAndLines(conn, orderToSave, dishOrders);

      applyStockMovementsForOrder(conn, deltaQuantities, savedOrder.getCreationDateTime());

      conn.commit();
      return savedOrder;

    } catch (SQLException e) {
      try {
        conn.rollback();
      } catch (SQLException ex) {
        throw new RuntimeException("Rollback failed", ex);
      }
      throw new RuntimeException(e);
    } finally {
      try {
        conn.setAutoCommit(true);
      } catch (SQLException ignored) {
      }
      dbConnection.close(conn);
    }
  }

  public Order findOrderByReference(String reference) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be null or blank");
    }

    Connection conn = dbConnection.getDBConnection();

    try {
      String findOrderSql =
          """
          SELECT id, reference, creation_datetime
          FROM "order"
          WHERE reference = ?
          """;

      int orderId;
      String savedReference;
      Instant creationDateTime;

      try (PreparedStatement ps = conn.prepareStatement(findOrderSql)) {
        ps.setString(1, reference);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) {
            throw new RuntimeException("Order not found (reference=" + reference + ")");
          }
          orderId = rs.getInt("id");
          savedReference = rs.getString("reference");
          creationDateTime = rs.getTimestamp("creation_datetime").toInstant();
        }
      }

      String findLinesSql =
          """
          SELECT dor.id            AS dish_order_id,
                 dor.id_dish       AS id_dish,
                 dor.quantity      AS quantity,
                 d.id              AS dish_id,
                 d.name            AS dish_name,
                 d.dish_type       AS dish_type,
                 d.price           AS dish_price
          FROM dish_order dor
          JOIN dish d ON d.id = dor.id_dish
          WHERE dor.id_order = ?
          """;

      List<DishOrder> dishOrders = new ArrayList<>();

      try (PreparedStatement ps = conn.prepareStatement(findLinesSql)) {
        ps.setInt(1, orderId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            Dish dish = mapDish(rs, "dish_id", "dish_name", "dish_price");
            int dishOrderId = rs.getInt("dish_order_id");
            int quantity = rs.getInt("quantity");
            dishOrders.add(new DishOrder(dishOrderId, dish, quantity));
          }
        }
      }

      return new Order(orderId, savedReference, creationDateTime, dishOrders);

    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      dbConnection.close(conn);
    }
  }

  private List<DishOrder> validateOrder(Order orderToSave) {
    if (orderToSave == null) {
      throw new IllegalArgumentException("orderToSave must not be null");
    }

    List<DishOrder> dishOrders = orderToSave.getDishOrders();
    if (dishOrders == null || dishOrders.isEmpty()) {
      throw new IllegalArgumentException("Order must contain at least one DishOrder");
    }
    return dishOrders;
  }

  private Map<Integer, Integer> aggregateDishQuantities(List<DishOrder> dishOrders) {
    Map<Integer, Integer> dishQuantities = new HashMap<>();

    for (DishOrder dishOrder : dishOrders) {
      if (dishOrder == null || dishOrder.getDish() == null) {
        throw new IllegalArgumentException("DishOrder and its Dish must not be null");
      }

      int dishId = dishOrder.getDish().getId();
      if (dishId <= 0) {
        throw new IllegalArgumentException("Dish id must be positive to save order");
      }

      dishQuantities.merge(dishId, dishOrder.getQuantity(), Integer::sum);
    }

    return dishQuantities;
  }

  private Map<Integer, Double> computeRequiredQuantities(
      Connection conn, Map<Integer, Integer> dishQuantities) throws SQLException {
    Map<Integer, Double> requiredQuantities = new HashMap<>();
    String sql =
        "SELECT id_ingredient, quantity_required, unit FROM dish_ingredient WHERE id_dish = ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (Map.Entry<Integer, Integer> entry : dishQuantities.entrySet()) {
        int dishId = entry.getKey();
        int totalDishQuantity = entry.getValue();

        ps.setInt(1, dishId);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            int ingredientId = rs.getInt("id_ingredient");
            double quantityPerDish = rs.getDouble("quantity_required");
            Unit unit = Unit.valueOf(rs.getString("unit"));

            double totalRequiredInSourceUnit = quantityPerDish * totalDishQuantity;
            double totalRequiredInKg =
                UnitConversionService.convert(
                    ingredientId, totalRequiredInSourceUnit, unit, Unit.KG);

            requiredQuantities.merge(ingredientId, totalRequiredInKg, Double::sum);
          }
        }
      }
    }

    return requiredQuantities;
  }

  private void checkStockOrThrow(
      Connection conn, Map<Integer, Double> requiredQuantities, Instant checkInstant)
      throws SQLException {
    for (Map.Entry<Integer, Double> entry : requiredQuantities.entrySet()) {
      int ingredientId = entry.getKey();
      double requiredQuantity = entry.getValue();

      Ingredient ingredient = loadIngredientWithMovements(conn, ingredientId);
      double availableQuantity = ingredient.getStockValueAt(checkInstant).getQuantity();

      if (availableQuantity < requiredQuantity) {
        throw new RuntimeException("Not enough stock for ingredient: " + ingredient.getName());
      }
    }
  }

  private Map<Integer, Integer> loadExistingDishQuantitiesForOrder(Connection conn, int orderId)
      throws SQLException {
    Map<Integer, Integer> dishQuantities = new HashMap<>();
    String sql = "SELECT id_dish, quantity FROM dish_order WHERE id_order = ?";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, orderId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          int dishId = rs.getInt("id_dish");
          int quantity = rs.getInt("quantity");
          dishQuantities.merge(dishId, quantity, Integer::sum);
        }
      }
    }

    return dishQuantities;
  }

  private Order upsertOrderAndLines(Connection conn, Order orderToSave, List<DishOrder> dishOrders)
      throws SQLException {

    String upsertOrderSql =
        """
        INSERT INTO "order"(id, reference, creation_datetime)
        VALUES (?, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET reference = EXCLUDED.reference,
            creation_datetime = EXCLUDED.creation_datetime
        RETURNING id, reference, creation_datetime
        """;

    int generatedOrderId;
    String savedReference;
    Instant savedCreationDateTime;

    try (PreparedStatement ps = conn.prepareStatement(upsertOrderSql)) {
      int idParam =
          orderToSave.getId() > 0
              ? orderToSave.getId()
              : getNextId(conn, "\"order\"", "Unable to generate new id for order");

      String reference = orderToSave.getReference();
      if (reference == null || reference.isBlank() || !reference.matches("ORD\\d{5}")) {
        reference = generateOrderReference(conn);
      }

      Instant creationDateTime =
          orderToSave.getCreationDateTime() != null
              ? orderToSave.getCreationDateTime()
              : Instant.now();

      ps.setInt(1, idParam);
      ps.setString(2, reference);
      ps.setTimestamp(3, Timestamp.from(creationDateTime));

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException("Failed to save order");
        }
        generatedOrderId = rs.getInt("id");
        savedReference = rs.getString("reference");
        savedCreationDateTime = rs.getTimestamp("creation_datetime").toInstant();
      }
    }

    try (PreparedStatement ps =
        conn.prepareStatement("DELETE FROM dish_order WHERE id_order = ?")) {
      ps.setInt(1, generatedOrderId);
      ps.executeUpdate();
    }

    String insertDishOrderSql =
        """
        INSERT INTO dish_order(id_order, id_dish, quantity)
        VALUES (?, ?, ?)
        """;

    try (PreparedStatement ps = conn.prepareStatement(insertDishOrderSql)) {
      for (DishOrder dishOrder : dishOrders) {
        int dishId = dishOrder.getDish().getId();
        ps.setInt(1, generatedOrderId);
        ps.setInt(2, dishId);
        ps.setInt(3, dishOrder.getQuantity());
        ps.addBatch();
      }
      ps.executeBatch();
    }

    return new Order(generatedOrderId, savedReference, savedCreationDateTime, dishOrders);
  }

  private String generateOrderReference(Connection conn) throws SQLException {
    String sql = "SELECT nextval('order_reference_seq') AS seq";
    try (PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      if (!rs.next()) {
        throw new RuntimeException("Unable to generate order reference from sequence");
      }
      long seq = rs.getLong("seq");
      return String.format("ORD%05d", seq);
    }
  }

  private Ingredient loadIngredientWithMovements(Connection conn, int ingredientId)
      throws SQLException {
    Ingredient ingredient;

    try (PreparedStatement ps =
        conn.prepareStatement("SELECT id, name, price, category FROM ingredient WHERE id = ?")) {
      ps.setInt(1, ingredientId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException("Ingredient not found (id=" + ingredientId + ")");
        }
        ingredient = mapIngredient(rs, "id", "name", "price");
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


  private void checkDuplicatesInList(List<Ingredient> ingredients) {
    Set<String> names = new HashSet<>();

    for (Ingredient ingredient : ingredients) {
      if (!names.add(ingredient.getName().toLowerCase())) {
        throw new RuntimeException(
            "Duplicate ingredient in provided list: " + ingredient.getName());
      }
    }
  }

  private Integer findIngredientIdByName(Connection conn, String name) throws SQLException {
    String sql = "SELECT id FROM ingredient WHERE name = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt("id");
        }
      }
    }
    return null;
  }

  private int findOrCreateIngredient(Connection conn, Ingredient ingredient) throws SQLException {
    Integer existingId = findIngredientIdByName(conn, ingredient.getName());
    if (existingId != null) {
      return existingId;
    }

    String insertSql =
        "INSERT INTO ingredient(name, category, price) VALUES (?, ?::ingredient_category, ?)"
            + " RETURNING id";
    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
      ps.setString(1, ingredient.getName());
      ps.setString(2, ingredient.getCategory().name());
      ps.setDouble(3, ingredient.getPrice());
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        return rs.getInt("id");
      }
    }

    throw new RuntimeException("Unable to find or create ingredient: " + ingredient.getName());
  }

  private boolean ingredientExists(Connection conn, String name) {
    try {
      return findIngredientIdByName(conn, name) != null;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private int getNextId(Connection conn, String tableRegclassLiteral, String errorMessage)
      throws SQLException {
    String sql = "SELECT nextval(pg_get_serial_sequence('" + tableRegclassLiteral + "', 'id'))";
    try (PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      if (rs.next()) {
        return rs.getInt(1);
      }
    }
    throw new RuntimeException(errorMessage);
  }

  private Dish mapDish(ResultSet rs, String idColumn, String nameColumn, String priceColumn)
      throws SQLException {
    return new Dish(
        rs.getInt(idColumn),
        rs.getString(nameColumn),
        DishTypeEnum.valueOf(rs.getString("dish_type")),
        rs.getObject(priceColumn) != null ? rs.getDouble(priceColumn) : null);
  }

  private Ingredient mapIngredient(
      ResultSet rs, String idColumn, String nameColumn, String priceColumn) throws SQLException {
    return new Ingredient(
        rs.getInt(idColumn),
        rs.getString(nameColumn),
        rs.getDouble(priceColumn),
        CategoryEnum.valueOf(rs.getString("category").toUpperCase()));
  }

  private List<Ingredient> mapIngredientsWithOptionalQuantity(ResultSet rs) throws SQLException {
    List<Ingredient> ingredients = new ArrayList<>();
    while (rs.next()) {
      Ingredient ingredient =
          mapIngredient(rs, "ingredient_id", "ingredient_name", "ingredient_price");

      if (rs.getObject("quantity_required") != null) {
        ingredient.setQuantity(rs.getDouble("quantity_required"));
      }

      ingredients.add(ingredient);
    }
    return ingredients;
  }

  private Ingredient upsertIngredient(Connection conn, Ingredient toSave) throws SQLException {
    String upsertSql =
        """
        INSERT INTO ingredient(id, name, category, price)
        VALUES (?, ?, ?::ingredient_category, ?)
        ON CONFLICT (id) DO UPDATE
        SET name = EXCLUDED.name,
            category = EXCLUDED.category,
            price = EXCLUDED.price
        RETURNING id, name, price, category
        """;

    try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
      int idParam =
          toSave.getId() > 0
              ? toSave.getId()
              : getNextId(conn, "ingredient", "Unable to generate new id for ingredient");
      ps.setInt(1, idParam);
      ps.setString(2, toSave.getName());
      ps.setString(3, toSave.getCategory().name());
      ps.setDouble(4, toSave.getPrice());

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          int ingredientId = rs.getInt("id");
          return new Ingredient(
              ingredientId,
              rs.getString("name"),
              rs.getDouble("price"),
              CategoryEnum.valueOf(rs.getString("category").toUpperCase()));
        }
      }
    }

    throw new RuntimeException("Failed to save ingredient: " + toSave.getName());
  }

  private void saveStockMovements(Connection conn, int ingredientId, List<StockMovement> movements)
      throws SQLException {
    String insertWithId =
        """
        INSERT INTO stock_movement(id, id_ingredient, quantity, type, unit, creation_datetime)
        VALUES (?, ?, ?, ?::movement_type, ?::unit_type, ?)
        ON CONFLICT (id) DO NOTHING
        """;

    String insertWithoutId =
        """
        INSERT INTO stock_movement(id_ingredient, quantity, type, unit, creation_datetime)
        VALUES (?, ?, ?::movement_type, ?::unit_type, ?)
        """;

    try (PreparedStatement psWithId = conn.prepareStatement(insertWithId);
        PreparedStatement psWithoutId = conn.prepareStatement(insertWithoutId)) {

      for (StockMovement movement : movements) {
        StockValue value = movement.getValue();

        if (movement.getId() > 0) {
          psWithId.setInt(1, movement.getId());
          psWithId.setInt(2, ingredientId);
          psWithId.setDouble(3, value.getQuantity());
          psWithId.setString(4, movement.getType().name());
          psWithId.setString(5, value.getUnit().name());
          psWithId.setTimestamp(6, Timestamp.from(movement.getCreationDateTime()));
          psWithId.addBatch();
        } else {
          psWithoutId.setInt(1, ingredientId);
          psWithoutId.setDouble(2, value.getQuantity());
          psWithoutId.setString(3, movement.getType().name());
          psWithoutId.setString(4, value.getUnit().name());
          psWithoutId.setTimestamp(5, Timestamp.from(movement.getCreationDateTime()));
          psWithoutId.addBatch();
        }
      }

      psWithId.executeBatch();
      psWithoutId.executeBatch();
    }
  }

  private void applyStockMovementsForOrder(
      Connection conn, Map<Integer, Double> requiredQuantities, Instant movementInstant)
      throws SQLException {
    for (Map.Entry<Integer, Double> entry : requiredQuantities.entrySet()) {
      int ingredientId = entry.getKey();
      double delta = entry.getValue();

      if (delta == 0.0) {
        continue;
      }

      MovementTypeEnum type;
      double quantity;
      if (delta > 0) {
        type = MovementTypeEnum.OUT;
        quantity = delta;
      } else {
        type = MovementTypeEnum.IN;
        quantity = -delta;
      }

      StockValue value = new StockValue(quantity, Unit.KG);
      StockMovement movement = new StockMovement(0, value, type, movementInstant);

      saveStockMovements(conn, ingredientId, List.of(movement));
    }
  }
}
