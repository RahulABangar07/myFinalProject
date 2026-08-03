package com.example.repository;

import com.example.annotation.LogStoredProcedure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductInventoryRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // The aspect will automatically map "itemId", "locationId", and "qty" parameters directly into your log file!
    @LogStoredProcedure("sp_UpdateWarehouseStock")
    public void updateStockLevels(int itemId, int locationId, int qty) {
        String query = "{call sp_UpdateWarehouseStock(?, ?, ?)}";
        jdbcTemplate.update(query, itemId, locationId, qty);
    }
}
