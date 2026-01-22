package org.example.service;

import org.example.model.*;
import org.example.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.sql.Types.INTEGER;

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
                Dish dish = new Dish(
                        rs.getInt("id"),
                        rs.getString("name"),
                        DishTypeEnum.valueOf(rs.getString("dish_type")),
                        rs.getObject("price") != null ? rs.getDouble("price") : null
                );

                dish.setIngredients(findIngredientsByDishId(dish.getId()));
                return dish;
            }
            throw new RuntimeException("Dish not found (id=" + id + ")");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(connection);
        }
    }

    private List<Ingredient> findIngredientsByDishId(int dishId) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (DishIngredient di : findDishIngredientsByDishId(dishId)) {
            Ingredient ingredient = di.getIngredient();
            ingredient.setQuantity(di.getQuantity());
            ingredients.add(ingredient);
        }
        return ingredients;
    }

    public List<DishIngredient> findDishIngredientsByDishId(int dishId) {
        List<DishIngredient> result = new ArrayList<>();
        String sql = """
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
                Dish dish = new Dish(
                        rs.getInt("dish_id"),
                        rs.getString("dish_name"),
                        DishTypeEnum.valueOf(rs.getString("dish_type")),
                        rs.getObject("dish_price") != null ? rs.getDouble("dish_price") : null
                );

                Ingredient ingredient = new Ingredient(
                        rs.getInt("ingredient_id"),
                        rs.getString("ingredient_name"),
                        rs.getDouble("ingredient_price"),
                        CategoryEnum.valueOf(rs.getString("category"))
                );

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
        List<Ingredient> ingredients = new ArrayList<>();
        int offset = (page - 1) * size;
        String sql = """
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
            while (rs.next()) {
                CategoryEnum category = CategoryEnum.valueOf(rs.getString("category").toUpperCase());
                Ingredient ingredient = new Ingredient(
                        rs.getInt("ingredient_id"),
                        rs.getString("ingredient_name"),
                        rs.getDouble("ingredient_price"),
                        category
                );

                if (rs.getObject("quantity_required") != null) {
                    ingredient.setQuantity(rs.getDouble("quantity_required"));
                }
                ingredients.add(ingredient);
            }
            return ingredients;
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
                            "Ingredient already exists in database: " + ingredient.getName()
                    );
                }
                String sql = "INSERT INTO ingredient(name, category, price) VALUES (?, ?::ingredient_category, ?)";
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
            } catch (SQLException e) {
                // ignore
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

            String upsertSql = """
                INSERT INTO ingredient(id, name, category, price)
                VALUES (?, ?, ?::ingredient_category, ?)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    category = EXCLUDED.category,
                    price = EXCLUDED.price
                RETURNING id, name, price, category
                """;

            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                int idParam = toSave.getId() > 0 ? toSave.getId() : getNextIngredientId(conn);
                ps.setInt(1, idParam);
                ps.setString(2, toSave.getName());
                ps.setString(3, toSave.getCategory().name());
                ps.setDouble(4, toSave.getPrice());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Ingredient(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getDouble("price"),
                                CategoryEnum.valueOf(rs.getString("category").toUpperCase())
                        );
                    }
                }
            }

            throw new RuntimeException("Failed to save ingredient: " + toSave.getName());

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(conn);
        }
    }

    public Dish saveDish(Dish dishToSave) {

        String upsertDishSql = """
        INSERT INTO dish(id, name, dish_type, price)
        VALUES (?, ?, ?::dish_type, ?) ON CONFLICT (id) DO UPDATE
        SET name = excluded.name, dish_type = excluded.dish_type, price = excluded.price
        RETURNING id, name, dish_type, price
    """;

        Connection conn = dbConnection.getDBConnection();
        int dishId;

        try {

                try (PreparedStatement ps = conn.prepareStatement(upsertDishSql)) {
                    Integer idParam = dishToSave.getId() > 0 ? dishToSave.getId() : getNextDishId(conn);
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

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM dish_ingredient WHERE id_dish = ?"
            )) {
                ps.setInt(1, dishId);
                ps.executeUpdate();
            }

            for (Ingredient ing : dishToSave.getIngredients()) {
                int ingredientId = findOrCreateIngredient(conn, ing);
                double quantity = ing.getQuantity() == null ? 1.0 : ing.getQuantity();
                Unit unit = Unit.KG;

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dish_ingredient(id_dish, id_ingredient, quantity_required, unit) VALUES (?, ?, ?, ?::unit_type)"
                )) {
                    ps.setInt(1, dishId);
                    ps.setInt(2, ingredientId);
                    ps.setDouble(3, quantity);
                    ps.setString(4, unit.name());
                    ps.executeUpdate();
                }
            }

            return findDishById(dishId);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(conn);
        }
    }

    public List<Dish> findDishByIngredientName(String ingredientName) {
        List<Dish> dishes = new ArrayList<Dish>();
        String sql = """
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
                Dish dish = new Dish(
                        rs.getInt("id"),
                        rs.getString("name"),
                        DishTypeEnum.valueOf(rs.getString("dish_type")),
                        rs.getObject("price") != null ? rs.getDouble("price") : null
                );
                dishes.add(dish);
            }

            return dishes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(connection);
        }
    }

    public List<Ingredient> findIngredientsByCriteria(String ingredientName, CategoryEnum category, String dishName, int page, int size){
        List<Ingredient> ingredients = new ArrayList<Ingredient>();
        int offset = (page - 1 ) * size;
        StringBuilder sql = new StringBuilder("""
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

            while (rs.next()) {

                Ingredient ingredient = new Ingredient(
                        rs.getInt("ingredient_id"),
                        rs.getString("ingredient_name"),
                        rs.getDouble("ingredient_price"),
                        CategoryEnum.valueOf(rs.getString("category").toUpperCase())
                );

                if (rs.getObject("quantity_required") != null) {
                    ingredient.setQuantity(rs.getDouble("quantity_required"));
                }

                ingredients.add(ingredient);
            }

            return ingredients;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(connection);
        }
    }

    private void checkDuplicatesInList(List<Ingredient> ingredients) {
        Set<String> names = new HashSet<>();

        for (Ingredient ingredient : ingredients) {
            if (!names.add(ingredient.getName().toLowerCase())) {
                throw new RuntimeException(
                        "Duplicate ingredient in provided list: " + ingredient.getName()
                );
            }
        }
    }

    private int findOrCreateIngredient(Connection conn, Ingredient ingredient) throws SQLException {
        String selectSql = "SELECT id FROM ingredient WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, ingredient.getName());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }

        String insertSql = "INSERT INTO ingredient(name, category, price) VALUES (?, ?::ingredient_category, ?) RETURNING id";
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
        String sql = "SELECT id FROM ingredient WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int getNextDishId(Connection conn) throws SQLException {
        String sql = "SELECT nextval(pg_get_serial_sequence('dish', 'id'))";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new RuntimeException("Unable to generate new id for dish");
    }

    private int getNextIngredientId(Connection conn) throws SQLException {
        String sql = "SELECT nextval(pg_get_serial_sequence('ingredient', 'id'))";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new RuntimeException("Unable to generate new id for ingredient");
    }

}
