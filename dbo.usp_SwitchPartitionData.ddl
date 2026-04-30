CREATE PROCEDURE dbo.usp_SwitchPartitionData
    @DatasetName NVARCHAR(100),
    @TargetDate DATE,
    @StagingTableName NVARCHAR(128) -- Fully qualified, e.g., 'dbo.Staging_New'
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @PartitionNumber INT;
    DECLARE @NewVersionID INT;
    DECLARE @ConstraintName NVARCHAR(200) = 'CK_Staging_PartitionCheck';
    DECLARE @Sql NVARCHAR(MAX);

    -- 1. Get Partition Number
    SET @PartitionNumber = $PARTITION.pf_ProcessDate(@TargetDate);

    -- 2. Validate: Ensure Staging Table contains ONLY data for @TargetDate
    DECLARE @InvalidCount INT;
    SET @Sql = N'SELECT @cnt = COUNT(*) FROM ' + @StagingTableName + N' WHERE ProcessDate <> @dt';
    EXEC sp_executesql @Sql, N'@dt DATE, @cnt INT OUTPUT', @dt = @TargetDate, @cnt = @InvalidCount OUTPUT;

    IF @InvalidCount > 0
    BEGIN
        RAISERROR('Validation Failed: Staging table contains rows for multiple or incorrect dates.', 16, 1);
        RETURN;
    END

    -- 3. Apply Trusted CHECK Constraint (Required for SWITCH IN)
    SET @Sql = N'IF OBJECT_ID(''' + @ConstraintName + ''') IS NOT NULL ALTER TABLE ' + @StagingTableName + ' DROP CONSTRAINT ' + @ConstraintName;
    EXEC(@Sql);

    SET @Sql = N'ALTER TABLE ' + @StagingTableName + ' WITH CHECK ADD CONSTRAINT ' + @ConstraintName + 
               ' CHECK (ProcessDate = ''' + CONVERT(NVARCHAR(10), @TargetDate, 121) + ''')';
    EXEC(@Sql);

    BEGIN TRY
        BEGIN TRANSACTION;

        -- 4. Swap Out: Archive the current live data to clear the partition
        SET @Sql = N'ALTER TABLE dbo.MainTable SWITCH PARTITION ' + CAST(@PartitionNumber AS NVARCHAR(10)) + 
                   N' TO dbo.ArchiveTable PARTITION ' + CAST(@PartitionNumber AS NVARCHAR(10));
        EXEC(@Sql);

        -- 5. Swap In: Move new, validated data from Staging to Main
        SET @Sql = N'ALTER TABLE ' + @StagingTableName + 
                   N' SWITCH TO dbo.MainTable PARTITION ' + CAST(@PartitionNumber AS NVARCHAR(10));
        EXEC(@Sql);

        -- 6. Atomic Metadata Update
        SELECT @NewVersionID = ISNULL(MAX(VersionID), 0) + 1 FROM dbo.ControlTable WHERE DatasetName = @DatasetName;

        -- Deactivate old version, activate new
        UPDATE dbo.ControlTable SET ReadyToUse = 0 WHERE DatasetName = @DatasetName AND ProcessDate = @TargetDate;

        INSERT INTO dbo.ControlTable (DatasetName, TargetTableName, ProcessDate, VersionID, ReadyToUse)
        VALUES (@DatasetName, 'MainTable', @TargetDate, @NewVersionID, 1);

        COMMIT TRANSACTION;
        PRINT 'Swap Complete: Version ' + CAST(@NewVersionID AS VARCHAR) + ' is now live.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END
