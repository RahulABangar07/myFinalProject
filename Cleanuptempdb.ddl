USE master;
GO

ALTER PROCEDURE dbo.sp_ProdSafeTempDBCleanup
    @TargetDataSizeMB INT = 4096,  
    @TargetLogSizeMB INT = 2048,
    @MaxAllowedCPUPct INT = 60 -- Abort threshold if CPU is too high
AS
BEGIN
    SET NOCOUNT ON;

    -- 1. PRODUCTION CPU SAFETY CHECK
    DECLARE @CurrentCPUPct INT;

    SELECT TOP 1 @CurrentCPUPct = 100 - r.SystemIdle
    FROM (
        SELECT record.value('(./Record/@id)[1]', 'int') AS record_id,
               record.value('(./Record/SchedulerMonitorEvent/SystemIdle)[1]', 'int') AS SystemIdle
        FROM (
            SELECT CAST(record AS xml) AS record
            FROM sys.dm_os_ring_buffers
            WHERE ring_buffer_type = N'RING_BUFFER_SCHEDULER_MONITOR'
              AND record LIKE '%<SystemIdle>%'
        ) AS x
    ) AS r
    ORDER BY record_id DESC;

    -- If CPU utilization is too high, exit immediately to protect production performance
    IF @CurrentCPUPct > @MaxAllowedCPUPct
    BEGIN
        PRINT 'PRODUCTION WARNING: CPU is currently at ' + CAST(@CurrentCPUPct AS VARCHAR(3)) + '%. Aborting tempdb cleanup to save resources.';
        RETURN;
    END

    -- 2. PRODUCTION SAFE CACHE CLEANUP
    DBCC FREESYSTEMCACHE ('TEMPDB') WITH NO_INFOMSGS;

    -- 3. CONTROLLED STEP-DOWN SHRINK LOOP
    DECLARE @FileName SYSNAME;
    DECLARE @FileType INT;
    DECLARE @CurrentSizePages INT;
    DECLARE @CurrentSizeMB INT;
    DECLARE @TargetSize INT;
    DECLARE @StepSizeMB INT = 512; 

    DECLARE file_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT name, type, size FROM sys.master_files WHERE database_id = 2;

    OPEN file_cursor;
    FETCH NEXT FROM file_cursor INTO @FileName, @FileType, @CurrentSizePages;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @CurrentSizeMB = (@CurrentSizePages * 8) / 1024;
        SET @TargetSize = CASE WHEN @FileType = 0 THEN @TargetDataSizeMB ELSE @TargetLogSizeMB END;

        WHILE @CurrentSizeMB > @TargetSize
        BEGIN
            SET @CurrentSizeMB = @CurrentSizeMB - @StepSizeMB;
            IF @CurrentSizeMB < @TargetSize SET @CurrentSizeMB = @TargetSize;

            DECLARE @Sql NVARCHAR(MAX);
            SET @Sql = N'USE tempdb; DBCC SHRINKFILE (' + QUOTENAME(@FileName) + N', ' + CAST(@CurrentSizeMB AS NVARCHAR(10)) + N') WITH NO_INFOMSGS;';
            
            BEGIN TRY
                EXEC sp_executesql @Sql;
            END TRY
            BEGIN CATCH
                PRINT 'File ' + @FileName + ' is blocked by active transactions. Moving to next file.';
                BREAK; 
            END CATCH

            WAITFOR DELAY '00:00:02'; -- Small delay to spread out storage I/O
        END

        FETCH NEXT FROM file_cursor INTO @FileName, @FileType, @CurrentSizePages;
    END

    CLOSE file_cursor;
    DEALLOCATE file_cursor;
END;
GO
