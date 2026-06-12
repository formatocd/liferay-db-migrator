package es.formatocd.liferay.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class LiferayDBMigratorMain {

    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("   Liferay DB Migration Tool (Java 21)       ");
        System.out.println("=============================================\n");

        LiferayDBMigratorConfig config = null;

        // 1. Obtener la configuración y capturar posibles errores de validación
        try {
            config = LiferayDBMigratorUtil.loadConfiguration(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR DE CONFIGURACIÓN] " + e.getMessage());
            System.err.print("Revisa los detalles del comando en: ");
            System.err.println("https://github.com/formatocd/liferay-db-migrator");
            System.exit(1);
        }

        // 2. Establecer conexiones usando directamente los getters del Record
        try (Connection myConn = DriverManager.getConnection(config.mysqlUrl(), config.mysqlUser(), config.mysqlPassword());
             Connection pgConn = DriverManager.getConnection(config.postgresUrl(), config.postgresUser(), config.postgresPassword())) {

            System.out.println("[OK] Conexiones establecidas correctamente.");

            pgConn.setAutoCommit(false);

            LiferayDBMigratorUtil.executeMigration(myConn, pgConn, config.batchSize());

        } catch (SQLException e) {
            System.err.println("\n[ERROR CRITICO DE BASE DE DATOS]");
            e.printStackTrace();
            System.exit(1);
        }
    }
}