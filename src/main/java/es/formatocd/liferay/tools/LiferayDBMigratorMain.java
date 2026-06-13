package es.formatocd.liferay.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class LiferayDBMigratorMain {

    private LiferayDBMigratorMain() {
    }

    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("   Liferay DB Migration Tool                 ");
        System.out.println("=============================================\n");

        LiferayDBMigratorConfig config = null;

        try {
            config = LiferayDBMigratorUtil.loadConfiguration(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[CONFIGURATION ERROR] " + e.getMessage());
            System.err.print("Check command details at: ");
            System.err.println("https://github.com/formatocd/liferay-db-migrator");
            System.exit(1);
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[CRITICAL ERROR] JDBC drivers not found in the classpath.");
            e.printStackTrace();
            System.exit(1);
        }

        try (Connection myConn = DriverManager.getConnection(config.mysqlUrl(), config.mysqlUser(), config.mysqlPassword());
             Connection pgConn = DriverManager.getConnection(config.postgresUrl(), config.postgresUser(), config.postgresPassword())) {

            System.out.println("[OK] Connections established successfully.");

            pgConn.setAutoCommit(false);

            LiferayDBMigratorUtil.executeMigration(myConn, pgConn, config.batchSize());

        } catch (SQLException e) {
            System.err.println("\n[CRITICAL DATABASE ERROR]");
            e.printStackTrace();
            System.exit(1);
        }
    }
}