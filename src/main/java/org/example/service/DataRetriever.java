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
        String sql = "SELECT id, name, price, category FROM ingredient WHERE id_dish = ?";
        Connection connection = dbConnection.getDBConnection();

        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, dishId);
            return getIngredients(ingredients, ps);
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
                   d.id AS dish_id, d.name AS dish_name, d.dish_type, d.price as dish_price
            FROM ingredient i
            LEFT JOIN dish d ON d.id = i.id_dish
            LIMIT ? OFFSET ?
            ORDER BY i.id
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
                        category,
                        getDisIngredient(rs)
                );
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
                if (ingredientExists(conn, ingredient.getName(), ingredient.getDish() != null ? ingredient.getDish().getId() : null)) {
                    throw new RuntimeException(
                            "Ingredient already exists in database: " + ingredient.getName()
                    );
                }
                String sql = "INSERT INTO ingredient(name, category, price, id_dish) VALUES (?, ?::ingredient_category, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, ingredient.getName());
                    ps.setString(2, ingredient.getCategory().name());
                    ps.setDouble(3, ingredient.getPrice());
                    if (ingredient.getDish() != null) {
                        ps.setInt(4, ingredient.getDish().getId());
                    } else {
                        ps.setNull(4, INTEGER);
                    }
                    ps.executeUpdate();
                }
            }

            conn.commit();
            return newIngredients;

        } catch (Exception e) {
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

    public Dish saveDish(Dish dishToSave) {

        String upsertDishSql = """
        INSERT INTO dish(id, name, dish_type, price)
        VALUES (?, ?, ?::dish_type, ?) ON CONFLICT (id) DO UPDATE SET name = excluded.name, dish_type = excluded.dish_type, price = excluded.price
        RETURNING id
    """;

        Connection conn = dbConnection.getDBConnection();
        int dishId;

        try {

                try (PreparedStatement ps = conn.prepareStatement(upsertDishSql)) {
                    Integer idParam = dishToSave.getId() > 0 ? dishToSave.getId() : null;
                    ps.setObject(1, idParam, INTEGER);
                    ps.setString(2, dishToSave.getName());
                    ps.setString(3, dishToSave.getDishType().name());
                    ps.setObject(4, dishToSave.getPrice());

                    if (ps.executeUpdate() == 0) {
                        throw new RuntimeException("Dish not found (id=" + dishToSave.getId() + ")");
                    }
                dishId = dishToSave.getId();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ingredient WHERE id_dish = ?"
            )) {
                ps.setInt(1, dishToSave.getId());
                ps.executeUpdate();
            }

            for (Ingredient ing : dishToSave.getIngredients()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ingredient(name, category, price, id_dish) VALUES (?, ?::ingredient_category, ?, ?)"
                )) {
                    ps.setString(1, ing.getName());
                    ps.setString(2, ing.getCategory().name());
                    ps.setDouble(3, ing.getPrice());
                    ps.setInt(4, dishToSave.getId());
                    ps.executeUpdate();
                }
            }

            return findDishById(dishToSave.getId());

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(conn);
        }
    }

    public List<Dish> findDishByIngredientName(String ingredientName) {
        List<Dish> dishes = new ArrayList<Dish>();
        String sql = "SELECT d.id, d.name, d.dish_type, d.price FROM dish d JOIN ingredient i ON i.id_dish = d.id WHERE i.name ILIKE ?";
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
            SELECT i.id AS ingredient_id, i.name AS ingredient_name, i.price, i.category,
                   d.id AS dish_id, d.name AS dish_name, d.dish_type
            FROM ingredient i
            LEFT JOIN dish d ON i.id_dish = d.id
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
                        rs.getDouble("price"),
                        CategoryEnum.valueOf(rs.getString("category").toUpperCase()),
                        getDisIngredient(rs)
                );

                ingredients.add(ingredient);
            }

            return ingredients;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.close(connection);
        }
    }

    private Dish getDisIngredient(ResultSet rs) throws SQLException {
        Dish dish = null;
        int dishId = rs.getInt("dish_id");
        String dishName = rs.getString("dish_name");
        Double dishPrice = rs.getObject("disih_price") != null ? rs.getDouble("price") : null;
        if (!rs.wasNull()) {
            String dt = rs.getString("dish_type");
            DishTypeEnum dishType = dt == null ? null : DishTypeEnum.valueOf(dt.toUpperCase());
            dish = new Dish(dishId, dishName, dishType, dishPrice);
        }
        return dish;
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

    private boolean ingredientExists(Connection conn, String name, Integer dishId) {
        String sql = "SELECT id FROM ingredient WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Ingredient> getIngredients(List<Ingredient> ingredients, PreparedStatement ps) throws SQLException {
        ResultSet resultSet = ps.executeQuery();

        while (resultSet.next()) {
            Ingredient ingredient = new Ingredient(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getDouble("price"),
                    CategoryEnum.valueOf(resultSet.getString("category")),
                    null
            );

            ingredients.add(ingredient);
        }
        return ingredients;
    }

}
