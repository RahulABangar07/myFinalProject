import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;

public class SqlFilterSplitter {

    public static void main(String[] args) {
        // Sample complex filter SQL input
        String filterSql = "SELECT * FROM my_table WHERE insighttype = 'Trend' " +
                "AND p.status = 'ACTIVE' " +
                "OR (pa.salary > 5000 AND age > 30)";

        System.out.println("--- Original SQL ---");
        System.out.println(filterSql + "\n");

        try {
            splitSql(filterSql);
        } catch (JSQLParserException e) {
            System.err.println("Failed to parse SQL: " + e.getMessage());
        }
    }

    public static void splitSql(String filterSql) throws JSQLParserException {
        // Parse the master query clones for independent modification
        Select selectInsight = (Select) CCJSqlParserUtil.parse(filterSql);
        Select selectPosition = (Select) CCJSqlParserUtil.parse(filterSql);
        Select selectAggr = (Select) CCJSqlParserUtil.parse(filterSql);
        Select selectClient = (Select) CCJSqlParserUtil.parse(filterSql);

        PlainSelect plainSelectInsight = selectInsight.getPlainSelect();
        PlainSelect plainSelectPosition = selectPosition.getPlainSelect();
        PlainSelect plainSelectAggr = selectAggr.getPlainSelect();
        PlainSelect plainSelectClient = selectClient.getPlainSelect();

        Expression whereClause = plainSelectInsight.getWhere();
        if (whereClause == null) {
            System.out.println("No WHERE clause found to process.");
            return;
        }

        // 1. Process insighttypesql (modifies conditions in place based on parent logic)
        Expression insightExpression = processInsightType(whereClause, false);
        plainSelectInsight.setWhere(insightExpression);

        // 2. Process other SQL variants by isolating specific prefixes
        plainSelectPosition.setWhere(isolateTargetFilters(whereClause, "p."));
        plainSelectAggr.setWhere(isolateTargetFilters(whereClause, "pa."));
        plainSelectClient.setWhere(isolateTargetFilters(whereClause, "no_prefix"));

        // Output results
        System.out.println("--- 1. insighttypesql ---");
        System.out.println(selectInsight);

        System.out.println("\n--- 2. positionfiltersql ---");
        System.out.println(selectPosition);

        System.out.println("\n--- 3. aggrpositionfiltersql ---");
        System.out.println(selectAggr);

        System.out.println("\n--- 4. clientdemosql ---");
        System.out.println(selectClient);
    }

    /**
     * Recursively traverses the expression tree to satisfy the insighttype logic.
     * Keeps 'insighttype', replaces others with 1=1 (under AND) or 1=0 (under OR).
     */
    private static Expression processInsightType(Expression expression, boolean isUnderOr) {
        if (expression instanceof BinaryExpression binaryExpr) {
            boolean currentOrContext = isUnderOr || (expression instanceof OrExpression);
            binaryExpr.setLeftExpression(processInsightType(binaryExpr.getLeftExpression(), currentOrContext));
            binaryExpr.setRightExpression(processInsightType(binaryExpr.getRightExpression(), currentOrContext));
            return binaryExpr;
        } else if (expression instanceof Parenthesis parenthesis) {
            parenthesis.setExpression(processInsightType(parenthesis.getExpression(), isUnderOr));
            return parenthesis;
        } else {
            // It's a leaf node expression (e.g., Column = Value)
            if (expression.toString().contains("insighttype")) {
                return expression; // Retain
            } else {
                // Replace based on parent context clause rules
                return isUnderOr ? new LongValue(0) : new LongValue(1); // JSqlParser simplifies '1' or '0' natively as expressions
            }
        }
    }

    /**
     * Recursively targets and retains only matching rules, discarding everything else.
     */
    private static Expression isolateTargetFilters(Expression expression, String targetRule) {
        if (expression instanceof BinaryExpression binaryExpr) {
            Expression left = isolateTargetFilters(binaryExpr.getLeftExpression(), targetRule);
            Expression right = isolateTargetFilters(binaryExpr.getRightExpression(), targetRule);

            if (left == null) return right;
            if (right == null) return left;

            binaryExpr.setLeftExpression(left);
            binaryExpr.setRightExpression(right);
            return binaryExpr;
        } else if (expression instanceof Parenthesis parenthesis) {
            Expression internal = isolateTargetFilters(parenthesis.getExpression(), targetRule);
            if (internal == null) return null;
            parenthesis.setExpression(internal);
            return parenthesis;
        }

        // Leaf Node Matching
        String exprStr = expression.toString();
        boolean matches = switch (targetRule) {
            case "p." -> exprStr.startsWith("p.");
            case "pa." -> exprStr.startsWith("pa.");
            case "no_prefix" -> !exprStr.contains(".") && !exprStr.contains("insighttype");
            default -> false;
        };

        return matches ? expression : null;
    }
}
