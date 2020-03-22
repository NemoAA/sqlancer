package sqlancer.sqlite3;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import sqlancer.sqlite3.schema.SQLite3Schema;
import sqlancer.sqlite3.schema.SQLite3Schema.Table;

public class SQLite3Helper {

	

	public static void dropTable(Connection con, Table table) throws SQLException {
		try (Statement s = con.createStatement()) {
			s.execute("DROP TABLE IF EXISTS " + table.getName());
		}
	}

	public static void deleteAllTables(Connection con) throws SQLException {
		SQLite3Schema previousSchema = SQLite3Schema.fromConnection(con);
		for (Table t : previousSchema.getDatabaseTables()) {
			dropTable(con, t);
		}
	}

}