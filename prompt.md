You are an expert Java and Sql senior engineer. Your task is to split a single master SQL query (referred to as `filterSql`) into 3 distinct, 
valid SQL queries based on strict logical rules.

Here are the names of the 4 target SQL queries you need to generate:
1. insightTypeFilterSql - Only condition starting with insightType
2. positionFilterSql include all condition starting with p. and pa. 
3. demographicFilterSql - All condition except starting with p. pa. and insightType

### Rule for generating `insightTypeFilterSql`:
From the original `filterSql`, isolate the logic related to `insighttype`. You must replace all other column properties/filters in the WHERE or HAVING clauses according to these rules:
- Replace the property with `1=1` if it is part of an AND clause (to preserve the logical truth of other conditions).
- Replace the property with `1=0` if it is part of an OR clause (to prevent the condition from falsely short-circuiting the OR logic). Use shortcircuiting approach if it does not have any other condition then it must be 1=1 . 

PositionFilterSql and aggregatePositionFilterSql works with each other - if ((p.broadAssetClassCode ='001' and p.statementMinorCode='0100' and pa.totalMarketValue> 20000) or (p.symbol='NVDA' and pa.totalMarketValue > 2000) then evaluate both conditions seperately on api_db.bv_positions_details table to find matching universalAccountNumber, accountNUmber, branchCode, symbol, quantity, totalQuantity, totalMarketValue, cusip. apply pa filter on each such occurance to find relevant information.
Persist this information and use it further to join this data with api_db.bv_accountNumber_hh_party_map 

finally use this data with #FilterRows which are filtered based on demographicFilterSql and insightTypeFilterSql and entitlementData
