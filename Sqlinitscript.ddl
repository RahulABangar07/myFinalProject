/*
===============================================================================
Deployment Script : deployment_runner.sql
Purpose           : Execute deployment stored procedures sequentially
Database          : Microsoft SQL Server
===============================================================================

Execution Example:

sqlcmd -S SERVER_NAME -d DATABASE_NAME -E -v SchemaName="dbo" -i deployment_runner.sql

OR

sqlcmd -S SERVER_NAME -d DATABASE_NAME -U username -P password ^
-v SchemaName="release_schema" -i deployment_runner.sql

===============================================================================
*/

SET NOCOUNT ON;

-------------------------------------------------------------------------------
-- Accept schema name from sqlcmd variable
-------------------------------------------------------------------------------
DECLARE @SchemaName NVARCHAR(128) = '$(SchemaName)';

-------------------------------------------------------------------------------
-- Variables
-------------------------------------------------------------------------------
DECLARE @SQL NVARCHAR(MAX);
DECLARE @ProcName NVARCHAR(200);
DECLARE @ExecutionOrder INT;

-------------------------------------------------------------------------------
-- Procedure execution list
-------------------------------------------------------------------------------
DECLARE @Procedures TABLE
(
    ExecutionOrder INT,
    ProcedureName NVARCHAR(200)
);

-------------------------------------------------------------------------------
-- Add procedures in required deployment order
-------------------------------------------------------------------------------
INSERT INTO @Procedures VALUES (1, 'usp_CreateTables');
INSERT INTO @Procedures VALUES (2, 'usp_CreateIndexes');
INSERT INTO @Procedures VALUES (3, 'usp_LoadMasterData');
INSERT INTO @Procedures VALUES (4, 'usp_UpdateConfigurations');
INSERT INTO @Procedures VALUES (5, 'usp_PostDeploymentValidation');

-------------------------------------------------------------------------------
-- Cursor for sequential execution
-------------------------------------------------------------------------------
DECLARE ProcCursor CURSOR FOR
SELECT ExecutionOrder, ProcedureName
FROM @Procedures
ORDER BY ExecutionOrder;

OPEN ProcCursor;

FETCH NEXT FROM ProcCursor
INTO @ExecutionOrder, @ProcName;

BEGIN TRY

    PRINT '====================================================';
    PRINT 'Deployment Started';
    PRINT 'Schema Name : ' + @SchemaName;
    PRINT 'Started At  : ' + CONVERT(VARCHAR, GETDATE(), 120);
    PRINT '====================================================';

    WHILE @@FETCH_STATUS = 0
    BEGIN

        -----------------------------------------------------------------------
        -- Build dynamic EXEC statement
        -----------------------------------------------------------------------
        SET @SQL =
            'EXEC ' +
            QUOTENAME(@SchemaName) +
            '.' +
            QUOTENAME(@ProcName);

        PRINT '----------------------------------------------------';
        PRINT 'Step       : ' + CAST(@ExecutionOrder AS VARCHAR);
        PRINT 'Executing  : ' + @SQL;

        -----------------------------------------------------------------------
        -- Execute procedure
        -----------------------------------------------------------------------
        EXEC sp_executesql @SQL;

        PRINT 'Completed  : ' + @ProcName;

        FETCH NEXT FROM ProcCursor
        INTO @ExecutionOrder, @ProcName;

    END

    PRINT '====================================================';
    PRINT 'Deployment Completed Successfully';
    PRINT 'Completed At : ' + CONVERT(VARCHAR, GETDATE(), 120);
    PRINT '====================================================';

END TRY
BEGIN CATCH

    PRINT '====================================================';
    PRINT 'Deployment Failed';
    PRINT 'Procedure     : ' + ISNULL(@ProcName, 'UNKNOWN');
    PRINT 'Error Number  : ' + CAST(ERROR_NUMBER() AS VARCHAR);
    PRINT 'Error Message : ' + ERROR_MESSAGE();
    PRINT 'Error Line    : ' + CAST(ERROR_LINE() AS VARCHAR);
    PRINT '====================================================';

    CLOSE ProcCursor;
    DEALLOCATE ProcCursor;

    THROW;

END CATCH

CLOSE ProcCursor;
DEALLOCATE ProcCursor;

GO
