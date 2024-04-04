package tech.ydb.jdbc.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.grpc.netty.shaded.io.netty.util.collection.IntObjectHashMap;
import io.grpc.netty.shaded.io.netty.util.collection.IntObjectMap;

import tech.ydb.jdbc.YdbConst;
import tech.ydb.jdbc.YdbTypes;
import tech.ydb.table.values.DecimalValue;
import tech.ydb.table.values.PrimitiveType;
import tech.ydb.table.values.Type;

public class YdbTypesImpl implements YdbTypes {

    private static final YdbTypesImpl INSTANCE = new YdbTypesImpl();

    private final IntObjectMap<Type> typeBySqlType;
    private final Map<PrimitiveType, Integer> sqlTypeByPrimitiveNumId;
    private final Map<Class<?>, Type> typeByClass;
    private final Map<String, Type> typeByTypeName;

    private YdbTypesImpl() {
        PrimitiveType[] values = PrimitiveType.values();
        typeByTypeName = new HashMap<>(values.length + 1);
        for (PrimitiveType type : values) {
            typeByTypeName.put(type.toString(), type);
        }

        typeByTypeName.put(DEFAULT_DECIMAL_TYPE.toString(), DEFAULT_DECIMAL_TYPE);

        // Add deprecated type names
        typeByTypeName.put("String", PrimitiveType.Bytes);
        typeByTypeName.put("Utf8", PrimitiveType.Text);


        typeBySqlType = new IntObjectHashMap<>(16);
        typeBySqlType.put(Types.VARCHAR, PrimitiveType.Text);
        typeBySqlType.put(Types.BIGINT, PrimitiveType.Int64);
        typeBySqlType.put(Types.TINYINT, PrimitiveType.Int8);
        typeBySqlType.put(Types.SMALLINT, PrimitiveType.Int16);
        typeBySqlType.put(Types.INTEGER, PrimitiveType.Int32);
        typeBySqlType.put(Types.REAL, PrimitiveType.Float);
        typeBySqlType.put(Types.FLOAT, PrimitiveType.Float);
        typeBySqlType.put(Types.DOUBLE, PrimitiveType.Double);
        typeBySqlType.put(Types.BIT, PrimitiveType.Bool);
        typeBySqlType.put(Types.BOOLEAN, PrimitiveType.Bool);
        typeBySqlType.put(Types.BINARY, PrimitiveType.Bytes);
        typeBySqlType.put(Types.VARBINARY, PrimitiveType.Bytes);
        typeBySqlType.put(Types.DATE, PrimitiveType.Date);
        typeBySqlType.put(Types.TIME, PrimitiveType.Datetime);
        typeBySqlType.put(Types.TIMESTAMP, PrimitiveType.Timestamp);
        typeBySqlType.put(Types.TIMESTAMP_WITH_TIMEZONE, PrimitiveType.TzTimestamp);
        typeBySqlType.put(Types.DECIMAL, DEFAULT_DECIMAL_TYPE);
        typeBySqlType.put(Types.NUMERIC, DEFAULT_DECIMAL_TYPE);

        typeByClass = new HashMap<>(32);
        typeByClass.put(String.class, PrimitiveType.Text);
        typeByClass.put(long.class, PrimitiveType.Int64);
        typeByClass.put(Long.class, PrimitiveType.Int64);
        typeByClass.put(BigInteger.class, PrimitiveType.Int64);
        typeByClass.put(byte.class, PrimitiveType.Int8);
        typeByClass.put(Byte.class, PrimitiveType.Int8);
        typeByClass.put(short.class, PrimitiveType.Int16);
        typeByClass.put(Short.class, PrimitiveType.Int16);
        typeByClass.put(int.class, PrimitiveType.Int32);
        typeByClass.put(Integer.class, PrimitiveType.Int32);
        typeByClass.put(float.class, PrimitiveType.Float);
        typeByClass.put(Float.class, PrimitiveType.Float);
        typeByClass.put(double.class, PrimitiveType.Double);
        typeByClass.put(Double.class, PrimitiveType.Double);
        typeByClass.put(boolean.class, PrimitiveType.Bool);
        typeByClass.put(Boolean.class, PrimitiveType.Bool);
        typeByClass.put(byte[].class, PrimitiveType.Bytes);
        typeByClass.put(Date.class, PrimitiveType.Timestamp);
        typeByClass.put(java.sql.Date.class, PrimitiveType.Date);
        typeByClass.put(LocalDate.class, PrimitiveType.Date);
        typeByClass.put(LocalDateTime.class, PrimitiveType.Datetime);
        typeByClass.put(Time.class, PrimitiveType.Datetime);
        typeByClass.put(LocalTime.class, PrimitiveType.Datetime);
        typeByClass.put(Timestamp.class, PrimitiveType.Timestamp);
        typeByClass.put(Instant.class, PrimitiveType.Timestamp);
        typeByClass.put(DecimalValue.class, DEFAULT_DECIMAL_TYPE);
        typeByClass.put(BigDecimal.class, DEFAULT_DECIMAL_TYPE);
        typeByClass.put(Duration.class, PrimitiveType.Interval);

        sqlTypeByPrimitiveNumId = new HashMap<>(values.length);
        for (PrimitiveType id : values) {
            final int sqlType;
            switch (id) {
                case Text:
                case Json:
                case JsonDocument:
                case Uuid:
                    sqlType = Types.VARCHAR;
                    break;
                case Bytes:
                case Yson:
                    sqlType = Types.BINARY;
                    break;
                case Bool:
                    sqlType = Types.BOOLEAN;
                    break;
                case Int8:
                case Int16:
                    sqlType = Types.SMALLINT;
                    break;
                case Uint8:
                case Int32:
                case Uint16:
                    sqlType = Types.INTEGER;
                    break;
                case Uint32:
                case Int64:
                case Uint64:
                case Interval:
                    sqlType = Types.BIGINT;
                    break;
                case Float:
                    sqlType = Types.FLOAT;
                    break;
                case Double:
                    sqlType = Types.DOUBLE;
                    break;
                case Date:
                    sqlType = Types.DATE;
                    break;
                case Datetime:
                    sqlType = Types.TIME;
                    break;
                case Timestamp:
                    sqlType = Types.TIMESTAMP;
                    break;
                case TzDate:
                case TzDatetime:
                case TzTimestamp:
                    sqlType = Types.TIMESTAMP_WITH_TIMEZONE;
                    break;
                default:
                    sqlType = Types.JAVA_OBJECT;
            }
            sqlTypeByPrimitiveNumId.put(id, sqlType);
        }

        this.selfValidate();
    }

    private void selfValidate() {
        for (Map.Entry<PrimitiveType, Integer> entry : sqlTypeByPrimitiveNumId.entrySet()) {
            int sqlType = entry.getValue();
            if (sqlType != Types.JAVA_OBJECT && !typeBySqlType.containsKey(sqlType)) {
                throw new IllegalStateException("Internal error. SQL type " + sqlType +
                        " by YDB type id " + entry.getKey() + " is not registered in #typeBySqlType");
            }
        }
    }

    @Override
    public int toWrappedSqlType(Class<?> type) {
        Type result = typeByClass.get(type);
        if (result != null) {
            return wrapYdbJdbcType(result);
        } else {
            return YdbConst.UNKNOWN_SQL_TYPE;
        }
    }

    @Override
    public Type toYdbType(Class<?> type) {
        return toYdbType(toWrappedSqlType(type));
    }

    @Override
    public int wrapYdbJdbcType(Type type) {
        if (type.getKind() == Type.Kind.PRIMITIVE) {
            return YdbConst.SQL_KIND_PRIMITIVE + ((PrimitiveType) type).ordinal();
        } else if (type.getKind() == Type.Kind.DECIMAL) {
            return Types.DECIMAL;
        } else if (type.getKind() == Type.Kind.OPTIONAL) {
            return wrapYdbJdbcType(type.unwrapOptional());
        } else {
            return Types.JAVA_OBJECT;
        }
    }

    @Override
    public int unwrapYdbJdbcType(int sqlType) {
        if (sqlType >= YdbConst.SQL_KIND_PRIMITIVE && sqlType < YdbConst.SQL_KIND_DECIMAL) {
            int idType = sqlType - YdbConst.SQL_KIND_PRIMITIVE;
            PrimitiveType type = PrimitiveType.values()[idType];

            Integer value = sqlTypeByPrimitiveNumId.get(type);
            if (value == null) {
                throw new RuntimeException("Internal error. Unsupported YDB type: " + idType + " as " + type);
            }
            return value;
        } else {
            return sqlType;
        }
    }

    @Override
    @Nullable
    public Type toYdbType(int sqlType) {
        if (sqlType == YdbConst.UNKNOWN_SQL_TYPE) {
            return null;
        } else if (sqlType >= YdbConst.SQL_KIND_PRIMITIVE && sqlType < YdbConst.SQL_KIND_DECIMAL) {
            int idType = sqlType - YdbConst.SQL_KIND_PRIMITIVE;
            return PrimitiveType.values()[idType];
        } else if (sqlType == YdbConst.SQL_KIND_DECIMAL || sqlType == Types.DECIMAL) {
            return DEFAULT_DECIMAL_TYPE;
        } else {
            return typeBySqlType.get(sqlType);
        }
    }

    @Nullable
    @Override
    public Type toYdbType(String typeName) {
        if (typeName.endsWith(YdbConst.OPTIONAL_TYPE_SUFFIX)) {
            Type type = typeByTypeName.get(typeName.substring(0, typeName.length() -
                    YdbConst.OPTIONAL_TYPE_SUFFIX.length()));
            return type != null ? type.makeOptional() : null;
        } else {
            return typeByTypeName.get(typeName);
        }
    }

    @Override
    public int toSqlType(Type type) {
        return unwrapYdbJdbcType(wrapYdbJdbcType(type));
    }


    @Override
    public int getSqlPrecision(Type type) {
        // The <...> column specifies the column size for the given column.
        // For numeric data, this is the maximum precision.
        // For character data, this is the length in characters.
        // For datetime datatypes, this is the length in characters of the String representation
        // (assuming the maximum allowed precision of the fractional seconds component).
        // For binary data, this is the length in bytes.
        // For the ROWID datatype, this is the length in bytes.
        // Null is returned for data types where the column size is not applicable.

        switch (type.getKind()) {
            case OPTIONAL:
                return getSqlPrecision(type.unwrapOptional());
            case DECIMAL:
                return 8 + 8;
            case PRIMITIVE:
                return getSqlPrecision((PrimitiveType) type);
            default:
                return 0; // unsupported?
        }
    }

    @Override
    public Collection<Integer> getSqlTypes() {
        return Collections.unmodifiableSet(typeBySqlType.keySet());
    }

    @Override
    public List<Type> getAllDatabaseTypes() {
        return Arrays.asList(
                PrimitiveType.Bool,
                PrimitiveType.Int8,
                PrimitiveType.Int16,
                PrimitiveType.Int32,
                PrimitiveType.Int64,
                PrimitiveType.Uint8,
                PrimitiveType.Uint16,
                PrimitiveType.Uint32,
                PrimitiveType.Uint64,
                PrimitiveType.Float,
                PrimitiveType.Double,
                PrimitiveType.Bytes,
                PrimitiveType.Text,
                PrimitiveType.Json,
                PrimitiveType.JsonDocument,
                PrimitiveType.Yson,
                PrimitiveType.Date,
                PrimitiveType.Datetime,
                PrimitiveType.Timestamp,
                PrimitiveType.Interval,
                YdbTypes.DEFAULT_DECIMAL_TYPE);
    }

    //

    private int getSqlPrecision(PrimitiveType type) {
        switch (type) {
            case Bool:
            case Int8:
            case Uint8:
                return 1;
            case Int16:
            case Uint16:
                return 2;
            case Int32:
            case Uint32:
            case Float:
                return 4;
            case Int64:
            case Uint64:
            case Double:
            case Interval:
                return 8;
            case Bytes:
            case Text:
            case Yson:
            case Json:
            case JsonDocument:
                return YdbConst.MAX_COLUMN_SIZE;
            case Uuid:
                return 8 + 8;
            case Date:
                return "0000-00-00".length();
            case Datetime:
                return "0000-00-00 00:00:00".length();
            case Timestamp:
                return "0000-00-00T00:00:00.000000".length();
            case TzDate:
                return "0000-00-00+00:00".length();
            case TzDatetime:
                return "0000-00-00 00:00:00+00:00".length();
            case TzTimestamp:
                return "0000-00-00T00:00:00.000000+00:00".length();
            default:
                return 0;
        }
    }


    // TODO: make access from connection only
    public static YdbTypes getInstance() {
        return INSTANCE;
    }
}
