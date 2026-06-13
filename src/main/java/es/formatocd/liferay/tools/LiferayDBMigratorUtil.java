package es.formatocd.liferay.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class LiferayDBMigratorUtil {

    private LiferayDBMigratorUtil() {
    }

    public static LiferayDBMigratorConfig loadConfiguration(String[] args) {
        String configFilePath = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config-file") && i + 1 < args.length) {
                configFilePath = args[i + 1];
                break;
            }
        }

        Properties props = new Properties();
        if (configFilePath != null) {
            try (FileInputStream fis = new FileInputStream(configFilePath)) {
                props.load(fis);
                System.out.println("[INFO] Configuration file loaded from: " + configFilePath);
            } catch (IOException e) {
                System.err.println("[WARN] Could not read the specified file: " + configFilePath);
            }
        }

        String mysqlUrl = getValue(args, props, "--mysql.url", "mysql.url", null);
        String mysqlUser = getValue(args, props, "--mysql.user", "mysql.user", null);
        String mysqlPassword = getValue(args, props, "--mysql.password", "mysql.password", null);

        String pgUrl = getValue(args, props, "--postgres.url", "postgres.url", null);
        String pgUser = getValue(args, props, "--postgres.user", "postgres.user", null);
        String pgPassword = getValue(args, props, "--postgres.password", "postgres.password", null);

        int batchSize = Integer.parseInt(getValue(args, props, "--batch.size", "batchSize", "5000"));

        return new LiferayDBMigratorConfig(mysqlUrl, mysqlUser, mysqlPassword, pgUrl, pgUser, pgPassword, batchSize);
    }

    public static void executeMigration(Connection myConn, Connection pgConn, int batchSize) throws SQLException {
        try {
            List<String> mysqlTables = getTables(myConn);
            List<String> pgTables = getPgTables(pgConn);

            cloneMissingTables(myConn, pgConn, mysqlTables, pgTables);

            disableForeignKeys(pgConn);

            System.out.println("[INFO] Found " + mysqlTables.size() + " tables to migrate. Starting process...\n");

            for (String table : mysqlTables) {
                if (pgTables.contains(table.toLowerCase(Locale.ROOT))) {
                    if (truncateTable(pgConn, table)) {
                        migrateTableData(myConn, pgConn, table, batchSize);
                    }
                }
            }

            System.out.println("\n[SUCCESS] Data transfer completed.");

        } finally {
            enableForeignKeys(pgConn);
        }
    }

    private static String getValue(String[] args, Properties props, String cliKey, String propKey, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(cliKey + "=")) {
                return arg.substring(cliKey.length() + 1);
            }
        }
        return props.getProperty(propKey, defaultValue);
    }

    private static void disableForeignKeys(Connection pgConn) throws SQLException {
        try (Statement stmt = pgConn.createStatement()) {
            stmt.execute("SET session_replication_role = 'replica';");
            System.out.println("[INFO] Foreign keys temporarily disabled in PostgreSQL.");
        }
    }

    private static void enableForeignKeys(Connection pgConn) throws SQLException {
        try (Statement stmt = pgConn.createStatement()) {
            stmt.execute("SET session_replication_role = 'origin';");
            System.out.println("[INFO] Foreign keys restored in PostgreSQL.");
        }
    }

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

    private static List<String> getPgTables(Connection pgConn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = pgConn.getMetaData();
        try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    private static void cloneMissingTables(Connection myConn, Connection pgConn, List<String> mysqlTables, List<String> pgTables) {
        System.out.println("[INFO] Verifying and cloning missing tables in PostgreSQL...");
        for (String table : mysqlTables) {
            String pgTable = table.toLowerCase(Locale.ROOT);
            if (!pgTables.contains(pgTable)) {
                System.out.println("  Cloning structure: " + table + " -> " + pgTable + "...");
                StringBuilder createStmt = new StringBuilder("CREATE TABLE ").append(pgTable).append(" (\n");
                List<String> colDefs = new ArrayList<>();
                List<String> pkCols = new ArrayList<>();

                try (Statement stmt = myConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + table)) {
                    while (rs.next()) {
                        String colName = rs.getString("Field").toLowerCase(Locale.ROOT);
                        String colTypeRaw = rs.getString("Type").toLowerCase(Locale.ROOT);
                        String key = rs.getString("Key");

                        String pgType = "text";
                        if (colTypeRaw.contains("tinyint")) pgType = "boolean";
                        else if (colTypeRaw.contains("bigint")) pgType = "bigint";
                        else if (colTypeRaw.contains("int")) pgType = "integer";
                        else if (colTypeRaw.contains("longtext") || colTypeRaw.contains("text")) pgType = "text";
                        else if (colTypeRaw.contains("varchar")) pgType = colTypeRaw;
                        else if (colTypeRaw.contains("datetime") || colTypeRaw.contains("timestamp")) pgType = "timestamp";
                        else if (colTypeRaw.contains("double")) pgType = "double precision";
                        else if (colTypeRaw.contains("float")) pgType = "real";
                        else if (colTypeRaw.contains("blob")) pgType = "bytea";

                        colDefs.add("    " + colName + " " + pgType);
                        if ("PRI".equalsIgnoreCase(key)) {
                            pkCols.add(colName);
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("  [ERROR] Could not read MySQL schema for " + table + ": " + e.getMessage());
                    continue;
                }

                if (!pkCols.isEmpty()) {
                    colDefs.add("    PRIMARY KEY (" + String.join(", ", pkCols) + ")");
                }
                createStmt.append(String.join(",\n", colDefs)).append("\n);");

                try (Statement pgStmt = pgConn.createStatement()) {
                    pgStmt.execute(createStmt.toString());
                    pgConn.commit();
                    pgTables.add(pgTable);
                } catch (SQLException e) {
                    System.err.println("  [ERROR] Failed to create table " + pgTable + ": " + e.getMessage());
                    try { pgConn.rollback(); } catch (SQLException ex) {}
                }
            }
        }
    }

    private static boolean truncateTable(Connection pgConn, String tableName) {
        String pgTable = tableName.toLowerCase(Locale.ROOT);
        try (Statement pgStmt = pgConn.createStatement()) {
            pgStmt.execute("TRUNCATE TABLE " + pgTable + " CASCADE;");
            pgConn.commit();
            return true;
        } catch (SQLException e) {
            System.out.println("[WARN] Could not TRUNCATE " + pgTable + " (Does it exist in the schema?). Skipping.");
            try { pgConn.rollback(); } catch (SQLException ex) {}
            return false;
        }
    }

    private static void migrateTableData(Connection myConn, Connection pgConn, String tableName, int batchSize) throws SQLException {
        String pgTable = tableName.toLowerCase(Locale.ROOT);

        List<String> oidColumns = new ArrayList<>();
        try (Statement stmt = pgConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '" + pgTable + "' AND data_type = 'oid'")) {
            while (rs.next()) {
                oidColumns.add(rs.getString("column_name").toLowerCase(Locale.ROOT));
            }
        }

        String selectSql = "SELECT * FROM " + tableName;

        try (Statement myStmt = myConn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            myStmt.setFetchSize(Integer.MIN_VALUE);
            
            try (ResultSet rsMy = myStmt.executeQuery(selectSql)) {

            ResultSetMetaData meta = rsMy.getMetaData();
            int colCount = meta.getColumnCount();
            
            StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(pgTable).append(" (");
            StringBuilder placeholders = new StringBuilder("VALUES (");
            
            for (int i = 1; i <= colCount; i++) {
                insertSql.append(meta.getColumnName(i).toLowerCase(Locale.ROOT));
                placeholders.append("?");
                if (i < colCount) {
                    insertSql.append(", ");
                    placeholders.append(", ");
                }
            }
            insertSql.append(") ").append(placeholders).append(")");

            org.postgresql.largeobject.LargeObjectManager lobjManager = null;
            if (!oidColumns.isEmpty()) {
                lobjManager = pgConn.unwrap(org.postgresql.PGConnection.class).getLargeObjectAPI();
            }

            try (PreparedStatement pgPs = pgConn.prepareStatement(insertSql.toString())) {
                int count = 0;
                while (rsMy.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        String colName = meta.getColumnName(i).toLowerCase(Locale.ROOT);
                        Object val = rsMy.getObject(i);
                        
                        if (val == null) {
                            pgPs.setNull(i, Types.NULL);
                            continue;
                        }

                        if (oidColumns.contains(colName)) {
                            byte[] data;
                            if (val instanceof String) {
                                data = ((String) val).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            } else if (val instanceof byte[]) {
                                data = (byte[]) val;
                            } else {
                                data = val.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            }
                            long oid = lobjManager.createLO(org.postgresql.largeobject.LargeObjectManager.READWRITE);
                            try (org.postgresql.largeobject.LargeObject obj = lobjManager.open(oid, org.postgresql.largeobject.LargeObjectManager.WRITE)) {
                                obj.write(data);
                            }
                            pgPs.setLong(i, oid);
                        }
                        else if (val instanceof Integer && meta.getColumnTypeName(i).equalsIgnoreCase("TINYINT")) {
                            pgPs.setBoolean(i, ((Integer) val) != 0);
                        } else if (val instanceof Boolean) {
                            pgPs.setBoolean(i, (Boolean) val);
                        } else {
                            pgPs.setObject(i, val);
                        }
                    }
                    pgPs.addBatch();
                    count++;

                    if (count % batchSize == 0) {
                        pgPs.executeBatch();
                        pgConn.commit();
                    }
                }
                
                if (count % batchSize != 0) {
                    pgPs.executeBatch();
                    pgConn.commit();
                }
                
                if (count > 0) {
                    System.out.println("[OK] " + pgTable + ": " + count + " records migrated.");
                }
            } catch (SQLException ex) {
                System.err.println("  [ERROR] Failed to insert into table " + pgTable + ": " + ex.getMessage());
                try { pgConn.rollback(); } catch (SQLException rEx) {}
            }
        }
    }
}