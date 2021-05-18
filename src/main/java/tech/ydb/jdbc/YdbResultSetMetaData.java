package tech.ydb.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface YdbResultSetMetaData extends ResultSetMetaData {

    /**
     * Returns column index by it's name, could be useful sometimes; basically because in YDB you should always
     * work with names (columns or parameters), indexes are just internal details
     *
     * @param columnName column name to find
     * @return column (1..N)
     * @throws SQLException if column is unknown
     */
    int getColumnIndex(String columnName) throws SQLException;

    /**
     * Returns all column names
     *
     * @return column names
     * @throws SQLException in case something bad happens
     */
    default Collection<String> getColumnNames() throws SQLException {
        int count = getColumnCount();
        List<String> columns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            columns.add(getColumnName(i + 1));
        }
        return columns;
    }
}
