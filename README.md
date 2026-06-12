# Liferay DB Migrator

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)

## 📌 Description

**Liferay DB Migrator** is a high-performance ETL (Extract, Transform, Load) tool written in Java 21, specifically designed to migrate Liferay databases from **MySQL** to **PostgreSQL**.

This migrator performs a massive data injection using batch processing with the official JDBC drivers. It temporarily disables referential integrity in PostgreSQL to guarantee maximum transfer speed without dependency locks.

---

## ⚙️ Correct Migration Flow (Important!)

This tool does not create the database structure (DDL); it transfers data in an optimized way. The correct procedure to follow is:

1. Stop the current Liferay instance connected to MySQL.
2. Create an empty PostgreSQL database.
3. Configure Liferay (portal-ext.properties or environment variables) to point to the new PostgreSQL database.
4. **Start the Liferay instance.** Liferay and its Hibernate engine will automatically create the entire table structure, sequences, and optimized indexes in PostgreSQL.
5. Stop the Liferay instance.
6. **Execute this tool (Liferay DB Migrator).** The script will clear the seed data (TRUNCATE) generated in step 4 and transfer all information from MySQL to PostgreSQL.
7. Start the Liferay instance again, which will now have all the migrated data (it is highly recommended to force a full reindex from the Control Panel).

---

## 🛠️ Build Instructions

### Prerequisites
* **Java 21** or higher.
* **Maven 3.8+**.

To generate the executable, clone the repository and run Maven in the project root. This will create a "Fat JAR" that includes the source code and the database JDBC drivers.

```
git clone https://github.com/formatocd/liferay-db-migrator.git
cd liferay-db-migrator
mvn clean package
```

The compiled file will be generated in the target/ directory as liferay-db-migrator-1.0.jar (or the corresponding version specified in your pom.xml).

---

## 🚀 Usage Instructions

The tool is designed to be executed via the terminal using Java. It uses a *Fail-Fast* architecture, meaning it will validate all configuration parameters before opening connections to prevent failed migrations midway through the process.

### Basic Syntax

```
java -jar liferay-db-migrator.jar [--config-file path/to/file] [additional_parameters]
```

### Available Parameters (CLI)

You can pass all configurations directly via console arguments. Parameters passed via CLI **take priority** and will override any value present in the properties file.

| Parameter | Description | Example |
| :--- | :--- | :--- |
| --config-file | Path to the .properties configuration file. | --config-file=/opt/migrator/db.properties |
| --mysql.url | JDBC connection URL for MySQL. | --mysql.url=jdbc:mysql://localhost:3306/lportal |
| --mysql.user | MySQL read user. | --mysql.user=root |
| --mysql.password | MySQL password. | --mysql.password=secret |
| --postgres.url | JDBC connection URL for PostgreSQL. | --postgres.url=jdbc:postgresql://localhost:5432/lportal |
| --postgres.user | PostgreSQL write user. | --postgres.user=postgres |
| --postgres.password| PostgreSQL password. | --postgres.password=secret |
| --batch.size | Number of records per insertion batch. (Default: 5000) | --batch.size=1000 |

### The Configuration File (properties)

The most convenient way to run the tool is by using a properties file. If you do not want to send credentials in plain text via the console, create a file (e.g., db-config.properties):

```
# ==========================================
# MySQL Configuration (Source)
# ==========================================
mysql.url=jdbc:mysql://192.168.1.10:3306/lportal?useSSL=false&serverTimezone=UTC
mysql.user=root
mysql.password=admin

# ==========================================
# PostgreSQL Configuration (Target)
# ==========================================
postgres.url=jdbc:postgresql://192.168.1.20:5432/lportal
postgres.user=postgres
postgres.password=admin

# ==========================================
# Performance Options
# ==========================================
# Adjust the size according to available RAM (Recommended: between 1000 and 5000)
batchSize=5000
```

To run it with this file:

```
java -jar liferay-db-migrator.jar --config-file db-config.properties
```

---

## 💡 Recommended Execution Scripts

To facilitate its use on servers (emulating the native structure of Liferay tools like portal-tools-db-upgrade-client), migrate.sh and migrate.bat scripts are provided. Place them in the same folder as the .jar file. 

These scripts automatically adjust the Java Virtual Machine (JVM) memory allocation to optimize massive data loading.

**Usage on Linux / macOS:**

```
chmod +x migrate.sh
./migrate.sh --config-file db-config.properties
```

**Usage on Windows:**

```
migrate.bat --config-file db-config.properties
```

---

## ⚠️ Additional Data and Recommendations

* **Native Type Translation:** The tool transparently manages the conversion of Liferay-specific types, correctly mapping MySQL TINYINT values to native BOOLEAN types in PostgreSQL.
* **Adjusting batch.size:** If the server has limited RAM, or if you have very heavy LONGTEXT / BLOB columns (e.g., Document Library embedded in the database instead of the file system), reduce the --batch.size to 1000 or 500 to avoid memory exhaustion or connection drops.
* **Document Library:** Remember that this script migrates **only the database**. The physical file repository (data/document_library) must remain intact in its original path on the server.