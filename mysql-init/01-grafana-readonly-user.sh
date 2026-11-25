#!/bin/bash
# Provisions a read-only MySQL user for Grafana. Runs once, at first container
# init, before Flyway ever connects - a database-level SELECT grant applies to
# all current *and future* tables in the schema, so it doesn't matter that this
# runs before the backend has created any tables yet.
#
# Grafana only ever needs to read this data for dashboards; giving it its own
# least-privilege account (instead of reusing the app's root credentials) keeps
# the "backend/JPA is the only schema owner" boundary intact - Grafana can look,
# not write. Plain .sql files in docker-entrypoint-initdb.d can't expand env
# vars, hence this being a shell script instead.
set -euo pipefail

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-SQL
    CREATE USER IF NOT EXISTS 'grafana'@'%' IDENTIFIED BY '${GRAFANA_MYSQL_PASSWORD}';
    GRANT SELECT ON \`${MYSQL_DATABASE}\`.* TO 'grafana'@'%';
    FLUSH PRIVILEGES;
SQL
