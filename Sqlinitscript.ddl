SET NOCOUNT ON;

DECLARE @SchemaName NVARCHAR(128) = 'dbo';
DECLARE @SQL NVARCHAR(MAX);
DECLARE @ProcName NVARCHAR(200);
DECLARE @ExecutionOrder INT;

DECLARE @Procedures TABLE
(
    ExecutionOrder INT,
    ProcedureName NVARCHAR(200)
);

-------------------------------------------------------------------------------
-- Add procedures in execution order
-------------------------------------------------------------------------------
INSERT INTO @Procedures VALUES (1, 'usp_CreateTables');
INSERT INTO @Procedures VALUES (2, 'usp_CreateIndexes');
INSERT INTO @Procedures VALUES (3, 'usp_LoadMasterData');
INSERT INTO @Procedures VALUES (4, 'usp_UpdateConfigurations');
INSERT INTO @Procedures VALUES (5, 'usp_PostDeploymentValidation');

DECLARE ProcCursor CURSOR FOR
SELECT ExecutionOrder, ProcedureName
FROM @Procedures
ORDER BY ExecutionOrder;

OPEN ProcCursor;

FETCH NEXT FROM ProcCursor
INTO @ExecutionOrder, @ProcName;

BEGIN TRY

    PRINT 'Deployment Started';

    WHILE @@FETCH_STATUS = 0
    BEGIN

        SET @SQL =
            'EXEC ' +
            QUOTENAME(@SchemaName) +
            '.' +
            QUOTENAME(@ProcName);

        PRINT '----------------------------------------------------';
        PRINT 'Step : ' + CAST(@ExecutionOrder AS VARCHAR);
        PRINT 'Executing : ' + @SQL;

        EXEC sp_executesql @SQL;

        PRINT 'Completed : ' + @ProcName;

        FETCH NEXT FROM ProcCursor
        INTO @ExecutionOrder, @ProcName;
    END

    PRINT 'Deployment Completed Successfully';

END TRY
BEGIN CATCH

    PRINT 'Deployment Failed';
    PRINT 'Procedure : ' + ISNULL(@ProcName, 'UNKNOWN');
    PRINT 'Error : ' + ERROR_MESSAGE();

    THROW;

END CATCH

CLOSE ProcCursor;
DEALLOCATE ProcCursor;
GO
