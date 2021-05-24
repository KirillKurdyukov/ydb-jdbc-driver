package tech.ydb.jdbc;

import java.util.List;

import javax.annotation.Nullable;

import tech.ydb.table.values.DecimalType;
import tech.ydb.table.values.Type;

public interface YdbTypes {
    DecimalType DEFAULT_DECIMAL_TYPE =
            DecimalType.of(YdbConst.SQL_DECIMAL_DEFAULT_PRECISION, YdbConst.SQL_DECIMAL_DEFAULT_SCALE);

    /**
     * Converts given Java class to sqlType
     *
     * @param type java class to convert
     * @return sqlType
     */
    int toSqlType(Class<?> type);

    /**
     * Converts given Java class to YDB type
     *
     * @param type java class to convert
     * @return YDB type
     */
    Type toYdbType(Class<?> type);

    /**
     * Converts given YDB type to custom (YDB-driver specific) sqlType
     *
     * @param type complete YDB type to convert
     * @return sqlType
     */
    int wrapYdbJdbcType(Type type);

    /**
     * Converts given sqlType to standard JDBC type
     *
     * @param sqlType probably customized sql type
     * @return standard JDBC type
     */
    int unwrapYdbJdbcType(int sqlType);

    /**
     * Converts given sql type to YDB type
     *
     * @param sqlType sql type to convert
     * @return YDB type or null, of sqlType cannot be converted
     */
    @Nullable
    Type toYdbType(int sqlType);

    /**
     * Converts given YDB type to standard SQL type
     *
     * @param type YDB type to convert
     * @return sqlType
     */
    int toSqlType(Type type);

    /**
     * Returns sql precision for given YDB type (or 0 if not applicable)
     *
     * @param type YDB type
     * @return precision
     */
    int getSqlPrecision(Type type);

    /**
     * Returns all types supported by database
     *
     * @return list of YDB types that supported by database (could be stored in columns)
     */
    List<Type> getDatabaseTypes();

}
