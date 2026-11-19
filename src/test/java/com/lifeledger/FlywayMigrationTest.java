package com.lifeledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations against PostgreSQL 16 (Testcontainers) and checks the result.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrations_createAll15Tables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name""", String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "roles", "user_roles", "repair_shops", "revoked_tokens", "audit_events",
                "product_categories", "products", "warranties", "product_access_grants",
                "service_records", "parts", "service_record_parts", "ownership_transfers", "ownership_history");
    }

    @Test
    void migrations_seedRolesAndCategories() {
        assertThat(jdbc.queryForList("SELECT name FROM roles", String.class))
                .containsExactlyInAnyOrder("ADMIN", "SELLER", "BUYER", "REPAIR_SHOP");
        assertThat(jdbc.queryForList("SELECT code FROM product_categories", String.class))
                .containsExactlyInAnyOrder("LAPTOP", "DESKTOP");
    }

    @Test
    void warranty_endBeforeStart_isRejectedByDatabase() {
        Long productId = insertProduct("WARRANTY-CHECK");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO warranties (product_id, provider, start_date, end_date)
                VALUES (?, 'Dell', DATE '2026-12-01', DATE '2026-01-01')""", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ownershipTransfers_secondOpenTransferForSameProduct_isRejected() {
        Long productId = insertProduct("OPEN-TRANSFER");
        Long buyerId = insertUser("open-transfer-buyer@example.com");
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM products WHERE id = ?", Long.class, productId);

        String insert = "INSERT INTO ownership_transfers (product_id, from_user_id, to_user_id) VALUES (?, ?, ?)";
        jdbc.update(insert, productId, ownerId, buyerId);

        assertThatThrownBy(() -> jdbc.update(insert, productId, ownerId, buyerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertUser(String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (name, email, password_hash) VALUES ('Test', ?, 'x') RETURNING id""",
                Long.class, email);
    }

    private Long insertProduct(String publicCode) {
        Long ownerId = insertUser(publicCode.toLowerCase() + "-owner@example.com");
        Long categoryId = jdbc.queryForObject("SELECT id FROM product_categories WHERE code = 'LAPTOP'", Long.class);
        return jdbc.queryForObject("""
                INSERT INTO products (public_code, serial_number, brand, model, owner_id, category_id)
                VALUES (?, 'SN-1', 'Dell', 'XPS 13', ?, ?) RETURNING id""",
                Long.class, publicCode, ownerId, categoryId);
    }
}
