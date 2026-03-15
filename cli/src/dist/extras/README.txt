SeedStream — extras/ directory
===============================

Place any additional JAR files here. They are automatically added to the
classpath when SeedStream starts.

JDBC DRIVERS
------------
SeedStream does not bundle JDBC drivers. To use the database destination,
download the appropriate driver JAR and place it here:

  PostgreSQL:  https://jdbc.postgresql.org/download/
               e.g. postgresql-42.7.3.jar

  MySQL:       https://dev.mysql.com/downloads/connector/j/
               Select "Platform Independent", extract the .jar
               e.g. mysql-connector-j-9.6.0.jar

  Other:       Any JDBC 4.x-compliant driver should work.

CUSTOM DATAFAKER PROVIDERS
---------------------------
SeedStream uses Datafaker for realistic data generation. You can extend
the available data types by adding a custom Datafaker provider JAR here.

A custom provider must:
  1. Implement a class extending AbstractProvider<BaseProviders>
  2. Register it via META-INF/services/net.datafaker.providers.base.AbstractProvider
  3. Be packaged as a JAR

See: https://www.datafaker.net/documentation/custom-providers/

EXAMPLE
-------
  extras/
  ├── postgresql-42.7.3.jar       <- JDBC driver for PostgreSQL
  └── my-custom-faker-1.0.0.jar   <- custom Datafaker provider
