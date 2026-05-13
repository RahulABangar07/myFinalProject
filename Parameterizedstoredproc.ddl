/*
===========================================================
AUTHOR      : DevOps DBA Wrapper
PURPOSE     : Dynamic DB + Schema Wrapper Executor
DESCRIPTION :
    - Accepts database name
    - Accepts schema name
    - Replaces {schemaName} placeholder
    - Executes stored procedures dynamically
    - Logs every execution step
===========================================================
*/

SET NOCOUNT ON;

BEGIN TRY

    -------------------------------------------------------
    -- INPUT PARAMETERS
    -------------------------------------------------------

    DECLARE @DbName        NVARCHAR(128) = 'MyApplicationDB';
    DECLARE @SchemaName    NVARCHAR(128) = 'finance';

    -------------------------------------------------------
    -- VARIABLES
    -------------------------------------------------------

    DECLARE @Sql                   NVARCHAR(MAX);
    DECLARE @ProcedureTemplate     NVARCHAR(500);
    DECLARE @ProcedureName         NVARCHAR(500);
    DECLARE @LogMessage            NVARCHAR(MAX);
    DECLARE @StartTime             DATETIME;
    DECLARE @EndTime               DATETIME;

    SET @StartTime = GETDATE();

    -------------------------------------------------------
    -- LOGGER : START
    -------------------------------------------------------

    SET @LogMessage =
        CONCAT(
            '[INFO] Wrapper execution started at ',
            CONVERT(VARCHAR, @StartTime, 120)
        );

    PRINT @LogMessage;

    -------------------------------------------------------
    -- VALIDATE DATABASE
    -------------------------------------------------------

    IF DB_ID(@DbName) IS NULL
    BEGIN
        RAISERROR('Database does not exist.', 16, 1);
    END

    PRINT CONCAT('[INFO] Database validated : ', @DbName);

    -------------------------------------------------------
    -- PROCEDURE TEMPLATE
    --
    -- Placeholder:
    --      {schemaName}
    -------------------------------------------------------

    SET @ProcedureTemplate =
        '{schemaName}.usp_ProcessCustomerData';

    PRINT CONCAT(
        '[INFO] Procedure template : ',
        @ProcedureTemplate
    );

    -------------------------------------------------------
    -- REPLACE SCHEMA PLACEHOLDER
    -------------------------------------------------------

    SET @ProcedureName =
        REPLACE(
            @ProcedureTemplate,
            '{schemaName}',
            QUOTENAME(@SchemaName)
        );

    PRINT CONCAT(
        '[INFO] Resolved procedure name : ',
        @ProcedureName
    );

    -------------------------------------------------------
    -- BUILD DYNAMIC SQL
    -------------------------------------------------------

    SET @Sql = '
        USE ' + QUOTENAME(@DbName) + ';

        EXEC ' + @ProcedureName + ';
    ';

    PRINT '[INFO] Dynamic SQL generated.';
    PRINT @Sql;

    -------------------------------------------------------
    -- EXECUTE SQL
    -------------------------------------------------------

    PRINT '[INFO] Executing stored procedure...';

    EXEC sp_executesql @Sql;

    PRINT '[INFO] Stored procedure executed successfully.';

    -------------------------------------------------------
    -- LOGGER : END
    -------------------------------------------------------

    SET @EndTime = GETDATE();

    SET @LogMessage =
        CONCAT(
            '[INFO] Wrapper execution completed at ',
            CONVERT(VARCHAR, @EndTime, 120),
            ' | Duration (ms): ',
            DATEDIFF(MILLISECOND, @StartTime, @EndTime)
        );

    PRINT @LogMessage;

END TRY

BEGIN CATCH

    -------------------------------------------------------
    -- ERROR LOGGING
    -------------------------------------------------------

    DECLARE @ErrorMessage      NVARCHAR(MAX);
    DECLARE @ErrorNumber       INT;
    DECLARE @ErrorLine         INT;
    DECLARE @ErrorProcedure    NVARCHAR(500);

    SET @ErrorMessage   = ERROR_MESSAGE();
    SET @ErrorNumber    = ERROR_NUMBER();
    SET @ErrorLine      = ERROR_LINE();
    SET @ErrorProcedure = ERROR_PROCEDURE();

    PRINT '================================================';
    PRINT '[ERROR] Wrapper execution failed.';
    PRINT CONCAT('[ERROR NUMBER] : ', @ErrorNumber);
    PRINT CONCAT('[ERROR MESSAGE]: ', @ErrorMessage);
    PRINT CONCAT('[ERROR LINE]   : ', @ErrorLine);
    PRINT CONCAT('[ERROR PROC]   : ', ISNULL(@ErrorProcedure, 'N/A'));
    PRINT '================================================';

    THROW;

END CATCH;
