CREATE PROCEDURE dbo.usp_MaintainRecentPartition
    @TableName NVARCHAR(256),
    @ProcessDate INT, -- Using INT to match your '20260429' format
    @FragThreshold FLOAT = 30.0 -- Only rebuild if fragmentation > 30%
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @PartitionNumber INT;
    DECLARE @CurrentFrag FLOAT;
    DECLARE @SQL NVARCHAR(MAX);
    DECLARE @ObjectID INT = OBJECT_ID(@TableName);

    -- 1. Identify which partition holds the specific ProcessDate
    SELECT TOP 1 @PartitionNumber = $PARTITION.pf_ProcessDate(@ProcessDate) -- Replace 'pf_ProcessDate' with your actual Function Name
    FROM sys.tables
    WHERE object_id = @ObjectID;

    IF @PartitionNumber IS NULL
    BEGIN
        PRINT 'Error: Could not identify partition for the provided date.';
        RETURN;
    END

    -- 2. Check Fragmentation for that specific partition
    SELECT @CurrentFrag = avg_fragmentation_in_percent
    FROM sys.dm_db_index_physical_stats(DB_ID(), @ObjectID, 1, @PartitionNumber, 'LIMITED');

    PRINT 'Partition ' + CAST(@PartitionNumber AS VARCHAR) + ' fragmentation is ' + CAST(@CurrentFrag AS VARCHAR) + '%';

    -- 3. Conditional Rebuild
    IF @CurrentFrag >= @FragThreshold
    BEGIN
        PRINT 'Fragmention above threshold. Rebuilding partition ' + CAST(@PartitionNumber AS VARCHAR) + '...';
        
        SET @SQL = 'ALTER INDEX ALL ON ' + @TableName + 
                   ' REBUILD PARTITION = ' + CAST(@PartitionNumber AS VARCHAR) + 
                   ' WITH (ONLINE = ON)'; -- Remove 'ONLINE = ON' if using Standard Edition
        EXEC sp_executesql @SQL;
    END
    ELSE
    BEGIN
        PRINT 'Fragmentation is low. Skipping rebuild.';
    END

    -- 4. Always update statistics for the modified partition
    PRINT 'Updating statistics for partition ' + CAST(@PartitionNumber AS VARCHAR) + '...';
    SET @SQL = 'UPDATE STATISTICS ' + @TableName + 
               ' WITH RESAMPLE ON PARTITIONS(' + CAST(@PartitionNumber AS VARCHAR) + ')';
    EXEC sp_executesql @SQL;

    PRINT 'Maintenance Complete.';
END;
GO


how to run
EXEC dbo.usp_MaintainRecentPartition 
    @TableName = 'YourTableName', 
    @ProcessDate = 20260429;
