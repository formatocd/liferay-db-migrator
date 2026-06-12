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
            if (mysqlUrl == null || mysqlUrl.isBlank()) throw new IllegalArgumentException("La URL de MySQL (mysql.url) es obligatoria.");
            if (mysqlUser == null || mysqlUser.isBlank()) throw new IllegalArgumentException("El usuario de MySQL (mysql.user) es obligatorio.");
            if (mysqlPassword == null || mysqlPassword.isBlank()) throw new IllegalArgumentException("La contraseña de MySQL (mysql.password) es obligatoria.");
            
            if (postgresUrl == null || postgresUrl.isBlank()) throw new IllegalArgumentException("La URL de PostgreSQL (postgres.url) es obligatoria.");
            if (postgresUser == null || postgresUser.isBlank()) throw new IllegalArgumentException("El usuario de PostgreSQL (postgres.user) es obligatorio.");
            if (postgresPassword == null || postgresPassword.isBlank()) throw new IllegalArgumentException("La contraseña de PostgreSQL (postgres.password) es obligatoria.");
            
            if (batchSize <= 0) throw new IllegalArgumentException("El tamaño del lote (batch.size) debe ser mayor que 0.");
        }

}
