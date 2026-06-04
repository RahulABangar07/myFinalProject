to check compute usage 

SELECT 
    start_time,
    end_time,
    -- CPU Metrics
    avg_cpu_percent AS [Avg_CPU_Utilization_%],
    max_cpu_percent AS [Peak_CPU_Utilization_%],
    -- Memory/Worker Metrics
    avg_data_io_percent AS [Avg_Data_IO_%],
    avg_log_write_percent AS [Avg_Log_Write_%],
    -- Storage Allocation vs Actual Usage
    (allocated_storage_in_megabytes) / 1024.0 AS [Allocated_Storage_GB],
    -- Calculated safe tier recommendation
    CASE 
        WHEN max_cpu_percent < 15 THEN 'Candidate for 50%+ vCore Downsize or Serverless Tier'
        WHEN max_cpu_percent >= 15 AND max_cpu_percent < 35 THEN 'Candidate for 1-Tier vCore Downsize'
        ELSE 'Keep Current Tier'
    END AS [Cost_Optimization_Action]
FROM sys.dm_db_resource_stats
ORDER BY start_time DESC;
