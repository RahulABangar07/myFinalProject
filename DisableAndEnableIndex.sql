CREATE PROCEDURE dbo.usp_DisableTableIndexes
    @SchemaName NVARCHAR(128),
    @TableName NVARCHAR(128)
WITH EXECUTE AS OWNER  -- Executes with owner privileges
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @IndexName NVARCHAR(128);
    DECLARE @SQL NVARCHAR(MAX);

    -- Cursor to fetch all active non-clustered indexes for the target table
    DECLARE index_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT i.name
    FROM sys.indexes i
    INNER JOIN sys.tables t ON i.object_id = t.object_id
    INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    WHERE s.name = @SchemaName
      AND t.name = @TableName
      AND i.type = 2          -- 2 = Non-Clustered Index
      AND i.is_disabled = 0;  -- Only fetch active indexes

    OPEN index_cursor;
    FETCH NEXT FROM index_cursor INTO @IndexName;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        -- Generate dynamic SQL to disable the index
        SET @SQL = N'ALTER INDEX ' + QUOTENAME(@IndexName) + 
                   N' ON ' + QUOTENAME(@SchemaName) + N'.' + QUOTENAME(@TableName) + 
                   N' DISABLE;';
        
        PRINT 'Disabling Index: ' + @IndexName;
        EXEC sp_executesql @SQL;

        FETCH NEXT FROM index_cursor INTO @IndexName;
    END;

    CLOSE index_cursor;
    DEALLOCATE index_cursor;
END;
GO



--_---------------enable index procedure

CREATE PROCEDURE dbo.usp_EnableIndexesAndRefreshStats
    @SchemaName NVARCHAR(128),
    @TableName NVARCHAR(128),
    @ProcessDate DATE
WITH EXECUTE AS OWNER  -- Executes with owner privileges
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @IndexName NVARCHAR(128);
    DECLARE @SQL NVARCHAR(MAX);
    DECLARE @PartitionNumber INT;

    -------------------------------------------------------------------
    -- 1. Rebuild and Enable Disabled Non-Clustered Indexes
    -------------------------------------------------------------------
    DECLARE index_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT i.name
    FROM sys.indexes i
    INNER JOIN sys.tables t ON i.object_id = t.object_id
    INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    WHERE s.name = @SchemaName
      AND t.name = @TableName
      AND i.type = 2          -- 2 = Non-Clustered Index
      AND i.is_disabled = 1;  -- Only fetch disabled indexes

    OPEN index_cursor;
    FETCH NEXT FROM index_cursor INTO @IndexName;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        -- Rebuild enables the index. ONLINE=ON allows concurrent reads if enterprise edition.
        SET @SQL = N'ALTER INDEX ' + QUOTENAME(@IndexName) + 
                   N' ON ' + QUOTENAME(@SchemaName) + N'.' + QUOTENAME(@TableName) + 
                   N' REBUILD WITH (ONLINE = ON);';
        
        PRINT 'Enabling/Rebuilding Index: ' + @IndexName;
        EXEC sp_executesql @SQL;

        FETCH NEXT FROM index_cursor INTO @IndexName;
    END;

    CLOSE index_cursor;
    DEALLOCATE index_cursor;

    -------------------------------------------------------------------
    -- 2. Find Partition Number & Compute Stats for @ProcessDate Only
    -------------------------------------------------------------------
    -- Identify which physical partition corresponds to the process date
    SELECT TOP 1 @PartitionNumber = p.partition_number
    FROM sys.partitions p
    INNER JOIN sys.indexes i ON p.object_id = i.object_id AND p.index_id = i.index_id
    INNER JOIN sys.tables t ON i.object_id = t.object_id
    INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    CROSS APPLY sys.dm_db_partition_range_values prv
    WHERE s.name = @SchemaName
      AND t.name = @TableName
      AND i.type IN (0,1) -- Heap or Clustered Index
      AND prv.value = @ProcessDate;

    -- If a specific partition is identified, update stats for that partition block only
    IF @PartitionNumber IS NOT NULL
    BEGIN
        SET @SQL = N'UPDATE STATISTICS ' + QUOTENAME(@SchemaName) + N'.' + QUOTENAME(@TableName) + 
                   N' WITH RESAMPLE ON PARTITIONS(' + CAST(@PartitionNumber AS NVARCHAR(10)) + N');';
        
        PRINT 'Computing statistics for Partition: ' + CAST(@PartitionNumber AS NVARCHAR(10));
        EXEC sp_executesql @SQL;
    END
    ELSE
    BEGIN
        PRINT 'Warning: Specific partition for date ' + CONVERT(VARCHAR, @ProcessDate, 120) + ' not found. Skipping targeted stats compute.';
    END;
END;
GO

