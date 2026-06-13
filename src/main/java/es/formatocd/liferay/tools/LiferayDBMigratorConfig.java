package es.formatocd.liferay.tools;

public record LiferayDBMigratorConfig(
    String mysqlUrl,
    String mysqlUser,
    String mysqlPassword,
    String postgresUrl,
    String postgresUser,
    String postgresPassword,
    int batchSize
) {
    
        public LiferayDBMigratorConfig {
            if (mysqlUrl == null || mysqlUrl.isBlank()) throw new IllegalArgumentException("MySQL URL (mysql.url) is required.");
            if (mysqlUser == null || mysqlUser.isBlank()) throw new IllegalArgumentException("MySQL user (mysql.user) is required.");
            if (mysqlPassword == null || mysqlPassword.isBlank()) throw new IllegalArgumentException("MySQL password (mysql.password) is required.");
            
            if (postgresUrl == null || postgresUrl.isBlank()) throw new IllegalArgumentException("PostgreSQL URL (postgres.url) is required.");
            if (postgresUser == null || postgresUser.isBlank()) throw new IllegalArgumentException("PostgreSQL user (postgres.user) is required.");
            if (postgresPassword == null || postgresPassword.isBlank()) throw new IllegalArgumentException("PostgreSQL password (postgres.password) is required.");
            
            if (batchSize <= 0) throw new IllegalArgumentException("Batch size (batch.size) must be greater than 0.");
        }

}
