package org.example.service;

import org.example.model.*;
import org.example.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataRetriever {

    private final DBConnection dbConnection;

    public DataRetriever(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public Dish findDishById(int id) {
        String sql = """
            SELECT id, name, dish_type
            FROM dish
            WHERE id = ?
            """;
        Connection connection = dbConnection.getDBConnection();

        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Dish dish = new Dish(
                        rs.getInt("id"),
                        rs.getString("name"),
                        DishTypeEnum.valueOf(rs.getString("dish_type"))
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
        String sql = """
            SELECT id, name, price, category
            FROM ingredient
            WHERE id_dish = ?
            """;
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
            SELECT i.id AS ingredient_id, i.name AS ingredient_name, i.price, i.category,
                   d.id AS dish_id, d.name AS dish_name, d.dish_type
            FROM ingredient i
            LEFT JOIN dish d ON d.id = i.id_dish
            LIMIT ? OFFSET ?
        """;

       Connection connection = dbConnection.getDBConnection();

        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, size);
            ps.setInt(2, offset);

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Dish dish = null;
                    int dishId = rs.getInt("dish_id");
                    if (!rs.wasNull()) {
                        String dt = rs.getString("dish_type");
                        DishTypeEnum dishType = dt == null ? null : DishTypeEnum.valueOf(dt.toUpperCase());
                        dish = new Dish(dishId, rs.getString("dish_name"), dishType);
                    }

                    CategoryEnum category = CategoryEnum.valueOf(rs.getString("category").toUpperCase());
                    Ingredient ingredient = new Ingredient(
                            rs.getInt("ingredient_id"),
                            rs.getString("ingredient_name"),
                            rs.getDouble("price"),
                            category,
                            dish
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
                if (ingredientExists(conn, ingredient.getName())) {
                    throw new RuntimeException(
                            "Ingredient already exists in database: " + ingredient.getName()
                    );
                }
                String sql = "INSERT INTO ingredient(name, category) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, ingredient.getName());
                    ps.setString(2, ingredient.getCategory().name());
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

    private boolean ingredientExists(Connection conn, String name) throws SQLException {
        String sql = "SELECT id FROM ingredient WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next();
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
