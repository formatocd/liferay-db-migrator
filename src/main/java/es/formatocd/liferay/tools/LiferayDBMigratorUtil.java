package es.formatocd.liferay.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class LiferayDBMigratorUtil {

    /**
     * Carga y procesa la configuración según el algoritmo definido.
     */
    public static LiferayDBMigratorConfig loadConfiguration(String[] args) {
        String configFilePath = null;

        // 1. Comprobar si hay parámetro --config-file
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config-file") && i + 1 < args.length) {
                configFilePath = args[i + 1];
                break;
            }
        }

        // 2. Cargar el objeto Properties SOLO si se ha indicado el fichero
        Properties props = new Properties();
        if (configFilePath != null) {
            try (FileInputStream fis = new FileInputStream(configFilePath)) {
                props.load(fis);
                System.out.println("[INFO] Archivo de configuración cargado desde: " + configFilePath);
            } catch (IOException e) {
                System.err.println("[WARN] No se pudo leer el archivo especificado: " + configFilePath);
            }
        }

        // 3. Extraer valores para mapearlos al Record
        String mysqlUrl = getValue(args, props, "--mysql.url", "mysql.url", null);
        String mysqlUser = getValue(args, props, "--mysql.user", "mysql.user", null);
        String mysqlPassword = getValue(args, props, "--mysql.password", "mysql.password", null);

        String pgUrl = getValue(args, props, "--postgres.url", "postgres.url", null);
        String pgUser = getValue(args, props, "--postgres.user", "postgres.user", null);
        String pgPassword = getValue(args, props, "--postgres.password", "postgres.password", null);

        int batchSize = Integer.parseInt(getValue(args, props, "--batch.size", "batchSize", "5000"));

        // 4. Retornar la nueva instancia (si faltan datos, el Record lanzará IllegalArgumentException)
        return new LiferayDBMigratorConfig(mysqlUrl, mysqlUser, mysqlPassword, pgUrl, pgUser, pgPassword, batchSize);
    }

    /**
     * Orquesta el flujo completo de la migración de datos.
     */
    public static void executeMigration(Connection myConn, Connection pgConn, int batchSize) throws SQLException {
        try {
            disableForeignKeys(pgConn);

            List<String> tables = getTables(myConn);
            System.out.println("[INFO] Se encontraron " + tables.size() + " tablas para migrar. Iniciando proceso...\n");

            for (String table : tables) {
                if (truncateTable(pgConn, table)) {
                    migrateTableData(myConn, pgConn, table, batchSize);
                }
            }

            System.out.println("\n[EXITO] Transferencia de datos completada.");

        } finally {
            enableForeignKeys(pgConn);
        }
    }

    // ====================================================================================
    // MÉTODOS PRIVADOS (Ocultamos la complejidad interna)
    // ====================================================================================

    /**
     * Método auxiliar para extraer el valor respetando la prioridad: CLI > Properties > Default
     */
    private static String getValue(String[] args, Properties props, String cliKey, String propKey, String defaultValue) {
        // Prioridad 1: Buscar en argumentos de consola
        for (String arg : args) {
            if (arg.startsWith(cliKey + "=")) {
                return arg.substring(cliKey.length() + 1);
            }
        }
        // Prioridad 2 y 3: Buscar en Properties o usar el valor por defecto
        return props.getProperty(propKey, defaultValue);
    }

    /**
     * Desactiva temporalmente la comprobación de claves foráneas y triggers en PostgreSQL
     * para la sesión actual, permitiendo inserciones masivas sin errores de dependencia.
     */
    private static void disableForeignKeys(Connection pgConn) throws SQLException {
        try (Statement stmt = pgConn.createStatement()) {
            stmt.execute("SET session_replication_role = 'replica';");
            System.out.println("[INFO] Claves foráneas desactivadas temporalmente en PostgreSQL.");
        }
    }

    /**
     * Restaura la comprobación normal de claves foráneas en PostgreSQL.
     */
    private static void enableForeignKeys(Connection pgConn) throws SQLException {
        try (Statement stmt = pgConn.createStatement()) {
            stmt.execute("SET session_replication_role = 'origin';");
            System.out.println("[INFO] Claves foráneas restauradas en PostgreSQL.");
        }
    }

    /**
     * Obtiene la lista de todas las tablas presentes en la base de datos MySQL.
     */
    private static List<String> getTables(Connection myConn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = myConn.getMetaData();
        try (ResultSet rs = metaData.getTables(myConn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    /**
     * Vacía una tabla en PostgreSQL de forma rápida y en cascada.
     */
    private static boolean truncateTable(Connection pgConn, String tableName) {
        String pgTable = tableName.toLowerCase();
        try (Statement pgStmt = pgConn.createStatement()) {
            pgStmt.execute("TRUNCATE TABLE " + pgTable + " CASCADE;");
            return true;
        } catch (SQLException e) {
            System.out.println("[WARN] No se pudo hacer TRUNCATE en " + pgTable + " (¿Aún no existe en el esquema?). Saltando.");
            return false;
        }
    }

    /**
     * Extrae los datos de una tabla en MySQL y los inserta por lotes en PostgreSQL.
     */
    private static void migrateTableData(Connection myConn, Connection pgConn, String tableName, int batchSize) throws SQLException {
        String pgTable = tableName.toLowerCase();
        String selectSql = "SELECT * FROM " + tableName;

        try (Statement myStmt = myConn.createStatement();
             ResultSet rsMy = myStmt.executeQuery(selectSql)) {

            ResultSetMetaData meta = rsMy.getMetaData();
            int colCount = meta.getColumnCount();
            
            // Construcción dinámica de la query de inserción
            StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(pgTable).append(" (");
            StringBuilder placeholders = new StringBuilder("VALUES (");
            
            for (int i = 1; i <= colCount; i++) {
                insertSql.append(meta.getColumnName(i).toLowerCase());
                placeholders.append("?");
                if (i < colCount) {
                    insertSql.append(", ");
                    placeholders.append(", ");
                }
            }
            insertSql.append(") ").append(placeholders).append(")");

            // Ejecución del Batch
            try (PreparedStatement pgPs = pgConn.prepareStatement(insertSql.toString())) {
                int count = 0;
                while (rsMy.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rsMy.getObject(i);
                        
                        // Traducción específica para Liferay: MySQL TINYINT a PostgreSQL BOOLEAN
                        if (val instanceof Integer && meta.getColumnTypeName(i).equalsIgnoreCase("TINYINT")) {
                            pgPs.setBoolean(i, ((Integer) val) != 0);
                        } else {
                            pgPs.setObject(i, val);
                        }
                    }
                    pgPs.addBatch();
                    count++;

                    if (count % batchSize == 0) {
                        pgPs.executeBatch();
                        pgConn.commit(); // Commit por cada lote completado
                    }
                }
                
                // Insertar el remanente que no completó un lote exacto
                pgPs.executeBatch();
                pgConn.commit();
                
                if (count > 0) {
                    System.out.println("[OK] " + pgTable + ": " + count + " registros migrados.");
                }
            }
        }
    }
}