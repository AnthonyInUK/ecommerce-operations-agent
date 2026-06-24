package com.alibaba.assistant.agent.start.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OlistRawCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(OlistRawCsvImporter.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final int BATCH_SIZE = 500;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public OlistRawCsvImporter(
            @org.springframework.beans.factory.annotation.Qualifier("warehouseDataSource") DataSource dataSource,
            JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void importDirectory(Path importDir) {
        List<ImportSpec> specs = buildSpecs();
        for (ImportSpec spec : specs) {
            importOne(spec, importDir.resolve(spec.fileName()));
        }
    }

    private void importOne(ImportSpec spec, Path csvFile) {
        if (!Files.exists(csvFile)) {
            log.info("OlistRawCsvImporter#importOne - reason=csv missing, table={}, file={}", spec.tableName(), csvFile);
            return;
        }

        jdbcTemplate.execute("TRUNCATE TABLE " + spec.tableName());

        String sql = buildInsertSql(spec.tableName(), spec.columns());
        int insertedRows = 0;
        try (BufferedReader reader = Files.newBufferedReader(csvFile);
             var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);

            String headerLine = readCsvRecord(reader);
            if (headerLine == null) {
                connection.commit();
                log.info("OlistRawCsvImporter#importOne - reason=empty csv, table={}, file={}", spec.tableName(), csvFile);
                return;
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(parseCsvLine(headerLine));
            validateHeaders(spec, headerIndex, csvFile);

            String line;
            int batchCount = 0;
            while ((line = readCsvRecord(reader)) != null) {
                if (line.isBlank()) {
                    continue;
                }

                List<String> values = parseCsvLine(line);
                for (int i = 0; i < spec.columns().size(); i++) {
                    ColumnSpec column = spec.columns().get(i);
                    String rawValue = headerIndex.containsKey(column.csvField())
                            ? values.get(headerIndex.get(column.csvField()))
                            : null;
                    bindValue(statement, i + 1, column.type(), rawValue);
                }
                statement.addBatch();
                batchCount++;
                insertedRows++;

                if (batchCount >= BATCH_SIZE) {
                    statement.executeBatch();
                    connection.commit();
                    batchCount = 0;
                }
            }

            if (batchCount > 0) {
                statement.executeBatch();
            }
            connection.commit();
            log.info("OlistRawCsvImporter#importOne - reason=raw csv imported, table={}, file={}, rowCount={}",
                    spec.tableName(), csvFile.getFileName(), insertedRows);
        } catch (IOException | SQLException ex) {
            throw new IllegalStateException("Failed to import Olist CSV into " + spec.tableName() + " from " + csvFile, ex);
        }
    }

    private static String buildInsertSql(String tableName, List<ColumnSpec> columns) {
        String columnSql = columns.stream().map(ColumnSpec::tableColumn).reduce((a, b) -> a + ", " + b).orElse("");
        String placeholderSql = columns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        return "INSERT INTO " + tableName + " (" + columnSql + ") VALUES (" + placeholderSql + ")";
    }

    private static Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerIndex.put(headers.get(i), i);
        }
        return headerIndex;
    }

    private static void validateHeaders(ImportSpec spec, Map<String, Integer> headerIndex, Path csvFile) {
        List<String> missing = spec.columns().stream()
                .map(ColumnSpec::csvField)
                .filter(field -> !headerIndex.containsKey(field))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("CSV header mismatch for " + csvFile + ", missing fields=" + missing);
        }
    }

    private static void bindValue(PreparedStatement statement, int index, ColumnType type, String rawValue) throws SQLException {
        String normalized = normalize(rawValue);
        if (normalized == null) {
            statement.setNull(index, type.jdbcType());
            return;
        }

        switch (type) {
            case STRING -> statement.setString(index, normalized);
            case INTEGER -> statement.setInt(index, Integer.parseInt(normalized));
            case DECIMAL -> statement.setBigDecimal(index, new BigDecimal(normalized));
            case TIMESTAMP -> statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.parse(normalized, TIMESTAMP_FORMAT)));
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static String readCsvRecord(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }

        StringBuilder record = new StringBuilder(line);
        while (!hasBalancedQuotes(record)) {
            String nextLine = reader.readLine();
            if (nextLine == null) {
                break;
            }
            record.append('\n').append(nextLine);
        }
        return record.toString();
    }

    private static boolean hasBalancedQuotes(CharSequence value) {
        boolean inQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < value.length() && value.charAt(i + 1) == '"') {
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
            }
        }
        return !inQuotes;
    }

    private static List<ImportSpec> buildSpecs() {
        return List.of(
                new ImportSpec("raw_olist_customers", "olist_customers_dataset.csv", List.of(
                        new ColumnSpec("customer_id", "customer_id", ColumnType.STRING),
                        new ColumnSpec("customer_unique_id", "customer_unique_id", ColumnType.STRING),
                        new ColumnSpec("customer_zip_code_prefix", "customer_zip_code_prefix", ColumnType.STRING),
                        new ColumnSpec("customer_city", "customer_city", ColumnType.STRING),
                        new ColumnSpec("customer_state", "customer_state", ColumnType.STRING)
                )),
                new ImportSpec("raw_olist_products", "olist_products_dataset.csv", List.of(
                        new ColumnSpec("product_id", "product_id", ColumnType.STRING),
                        new ColumnSpec("product_category_name", "product_category_name", ColumnType.STRING),
                        new ColumnSpec("product_name_length", "product_name_lenght", ColumnType.INTEGER),
                        new ColumnSpec("product_description_length", "product_description_lenght", ColumnType.INTEGER),
                        new ColumnSpec("product_photos_qty", "product_photos_qty", ColumnType.INTEGER),
                        new ColumnSpec("product_weight_g", "product_weight_g", ColumnType.INTEGER),
                        new ColumnSpec("product_length_cm", "product_length_cm", ColumnType.INTEGER),
                        new ColumnSpec("product_height_cm", "product_height_cm", ColumnType.INTEGER),
                        new ColumnSpec("product_width_cm", "product_width_cm", ColumnType.INTEGER)
                )),
                new ImportSpec("raw_olist_orders", "olist_orders_dataset.csv", List.of(
                        new ColumnSpec("order_id", "order_id", ColumnType.STRING),
                        new ColumnSpec("customer_id", "customer_id", ColumnType.STRING),
                        new ColumnSpec("order_status", "order_status", ColumnType.STRING),
                        new ColumnSpec("order_purchase_timestamp", "order_purchase_timestamp", ColumnType.TIMESTAMP),
                        new ColumnSpec("order_approved_at", "order_approved_at", ColumnType.TIMESTAMP),
                        new ColumnSpec("order_delivered_carrier_date", "order_delivered_carrier_date", ColumnType.TIMESTAMP),
                        new ColumnSpec("order_delivered_customer_date", "order_delivered_customer_date", ColumnType.TIMESTAMP),
                        new ColumnSpec("order_estimated_delivery_date", "order_estimated_delivery_date", ColumnType.TIMESTAMP)
                )),
                new ImportSpec("raw_olist_order_items", "olist_order_items_dataset.csv", List.of(
                        new ColumnSpec("order_id", "order_id", ColumnType.STRING),
                        new ColumnSpec("order_item_id", "order_item_id", ColumnType.INTEGER),
                        new ColumnSpec("product_id", "product_id", ColumnType.STRING),
                        new ColumnSpec("seller_id", "seller_id", ColumnType.STRING),
                        new ColumnSpec("shipping_limit_date", "shipping_limit_date", ColumnType.TIMESTAMP),
                        new ColumnSpec("price", "price", ColumnType.DECIMAL),
                        new ColumnSpec("freight_value", "freight_value", ColumnType.DECIMAL)
                )),
                new ImportSpec("raw_olist_payments", "olist_order_payments_dataset.csv", List.of(
                        new ColumnSpec("order_id", "order_id", ColumnType.STRING),
                        new ColumnSpec("payment_sequential", "payment_sequential", ColumnType.INTEGER),
                        new ColumnSpec("payment_type", "payment_type", ColumnType.STRING),
                        new ColumnSpec("payment_installments", "payment_installments", ColumnType.INTEGER),
                        new ColumnSpec("payment_value", "payment_value", ColumnType.DECIMAL)
                )),
                new ImportSpec("raw_olist_reviews", "olist_order_reviews_dataset.csv", List.of(
                        new ColumnSpec("review_id", "review_id", ColumnType.STRING),
                        new ColumnSpec("order_id", "order_id", ColumnType.STRING),
                        new ColumnSpec("review_score", "review_score", ColumnType.INTEGER),
                        new ColumnSpec("review_comment_title", "review_comment_title", ColumnType.STRING),
                        new ColumnSpec("review_comment_message", "review_comment_message", ColumnType.STRING),
                        new ColumnSpec("review_creation_date", "review_creation_date", ColumnType.TIMESTAMP),
                        new ColumnSpec("review_answer_timestamp", "review_answer_timestamp", ColumnType.TIMESTAMP)
                )),
                new ImportSpec("raw_olist_geolocation", "olist_geolocation_dataset.csv", List.of(
                        new ColumnSpec("geolocation_zip_code_prefix", "geolocation_zip_code_prefix", ColumnType.STRING),
                        new ColumnSpec("geolocation_lat", "geolocation_lat", ColumnType.DECIMAL),
                        new ColumnSpec("geolocation_lng", "geolocation_lng", ColumnType.DECIMAL),
                        new ColumnSpec("geolocation_city", "geolocation_city", ColumnType.STRING),
                        new ColumnSpec("geolocation_state", "geolocation_state", ColumnType.STRING)
                ))
        );
    }

    private record ImportSpec(String tableName, String fileName, List<ColumnSpec> columns) {
        private ImportSpec {
            Objects.requireNonNull(tableName);
            Objects.requireNonNull(fileName);
            Objects.requireNonNull(columns);
        }
    }

    private record ColumnSpec(String tableColumn, String csvField, ColumnType type) {
    }

    private enum ColumnType {
        STRING(Types.VARCHAR),
        INTEGER(Types.INTEGER),
        DECIMAL(Types.DECIMAL),
        TIMESTAMP(Types.TIMESTAMP);

        private final int jdbcType;

        ColumnType(int jdbcType) {
            this.jdbcType = jdbcType;
        }

        public int jdbcType() {
            return jdbcType;
        }
    }
}
