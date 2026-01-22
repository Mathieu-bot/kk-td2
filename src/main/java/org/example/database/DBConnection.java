package org.example.database;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

  private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

  private final String JDBC_URL = dotenv.get("JDBC_URL");
  private final String USERNAME = dotenv.get("USERNAME");
  private final String PASSWORD = dotenv.get("PASSWORD");

  public Connection getDBConnection() {
    try {
      return DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
    } catch (SQLException e) {
      throw new RuntimeException("Unable to connect to database", e);
    }
  }

  public void close(Connection connection) {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
