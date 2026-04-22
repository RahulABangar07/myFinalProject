import java.util.*;

public class SqlQueryBuilder {

    // ================= CORE TYPES =================

    public interface Table {
        String getName();
        String getAlias();
    }

    public interface Column<T> {
        String getName();
        Table getTable();
    }

    public static class TableImpl implements Table {
        private final String name;
        private final String alias;

        public TableImpl(String name, String alias) {
            this.name = name;
            this.alias = alias;
        }

        public String getName() { return name; }
        public String getAlias() { return alias; }
    }

    public static class ColumnImpl<T> implements Column<T> {
        private final String name;
        private final Table table;

        public ColumnImpl(String name, Table table) {
            this.name = name;
            this.table = table;
        }

        public String getName() { return name; }
        public Table getTable() { return table; }

        @Override
        public String toString() {
            return table.getAlias() + "." + name;
        }
    }

    // ================= ENUMS =================

    public enum SortOrder {
        ASC, DESC
    }

    public enum JoinType {
        INNER, LEFT, RIGHT, FULL
    }

    // ================= JOIN =================

    static class JoinCondition {
        Column<?> left;
        Column<?> right;

        JoinCondition(Column<?> left, Column<?> right) {
            this.left = left;
            this.right = right;
        }
    }

    static class Join {
        JoinType type;
        Table table;
        List<JoinCondition> conditions = new ArrayList<>();

        Join(JoinType type, Table table) {
            this.type = type;
            this.table = table;
        }
    }

    public class JoinBuilder {
        private final Join join;

        JoinBuilder(Join join) {
            this.join = join;
        }

        public JoinBuilder on(Column<?> left, Column<?> right) {
            join.conditions.add(new JoinCondition(left, right));
            return this;
        }

        public SqlQueryBuilder endJoin() {
            return SqlQueryBuilder.this;
        }
    }

    // ================= INTERNAL STATE =================

    private List<String> selectColumns = new ArrayList<>();
    private Table fromTable;

    private List<String> whereConditions = new ArrayList<>();
    private List<String> groupByColumns = new ArrayList<>();
    private List<String> havingConditions = new ArrayList<>();
    private List<String> orderByColumns = new ArrayList<>();

    private List<Join> joins = new ArrayList<>();

    private Integer offset;
    private Integer fetch;

    // ================= SELECT =================

    public SqlQueryBuilder select(Column<?> column) {
        selectColumns.add(column.toString());
        return this;
    }

    public SqlQueryBuilder selectRaw(String expression) {
        selectColumns.add(expression);
        return this;
    }

    // Aggregates
    public SqlQueryBuilder sum(Column<?> column, String alias) {
        selectColumns.add("SUM(" + column + ") AS " + alias);
        return this;
    }

    public SqlQueryBuilder count(Column<?> column, String alias) {
        selectColumns.add("COUNT(" + column + ") AS " + alias);
        return this;
    }

    public SqlQueryBuilder max(Column<?> column, String alias) {
        selectColumns.add("MAX(" + column + ") AS " + alias);
        return this;
    }

    public SqlQueryBuilder min(Column<?> column, String alias) {
        selectColumns.add("MIN(" + column + ") AS " + alias);
        return this;
    }

    public SqlQueryBuilder avg(Column<?> column, String alias) {
        selectColumns.add("AVG(" + column + ") AS " + alias);
        return this;
    }

    // ================= FROM =================

    public SqlQueryBuilder from(Table table) {
        this.fromTable = table;
        return this;
    }

    // ================= JOIN =================

    public JoinBuilder join(JoinType type, Table table) {
        Join join = new Join(type, table);
        joins.add(join);
        return new JoinBuilder(join);
    }

    // ================= WHERE =================

    public <T> SqlQueryBuilder where(Column<T> column, String operator, T value) {
        whereConditions.add(column + " " + operator + " " + formatValue(value));
        return this;
    }

    // ================= GROUP BY =================

    public SqlQueryBuilder groupBy(Column<?> column) {
        groupByColumns.add(column.toString());
        return this;
    }

    // ================= HAVING =================

    public SqlQueryBuilder having(String condition) {
        havingConditions.add(condition);
        return this;
    }

    // ================= ORDER BY =================

    public SqlQueryBuilder orderBy(Column<?> column, SortOrder order) {
        orderByColumns.add(column + " " + order.name());
        return this;
    }

    public SqlQueryBuilder orderByAlias(String alias, SortOrder order) {
        orderByColumns.add(alias + " " + order.name());
        return this;
    }

    // ================= PAGINATION =================

    public SqlQueryBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    public SqlQueryBuilder fetch(int fetch) {
        this.fetch = fetch;
        return this;
    }

    // ================= BUILD =================

    public String build() {

        if (selectColumns.isEmpty() || fromTable == null) {
            throw new IllegalStateException("SELECT and FROM are mandatory");
        }

        StringBuilder query = new StringBuilder();

        // SELECT
        query.append("SELECT ")
             .append(String.join(", ", selectColumns))
             .append(" FROM ")
             .append(fromTable.getName())
             .append(" ")
             .append(fromTable.getAlias());

        // JOIN
        for (Join j : joins) {

            if (j.conditions.isEmpty()) {
                throw new IllegalStateException("JOIN must have ON condition");
            }

            query.append(" ")
                 .append(j.type.name())
                 .append(" JOIN ")
                 .append(j.table.getName())
                 .append(" ")
                 .append(j.table.getAlias())
                 .append(" ON ");

            List<String> conditions = new ArrayList<>();
            for (JoinCondition c : j.conditions) {
                conditions.add(c.left + " = " + c.right);
            }

            query.append(String.join(" AND ", conditions));
        }

        // WHERE
        if (!whereConditions.isEmpty()) {
            query.append(" WHERE ")
                 .append(String.join(" AND ", whereConditions));
        }

        // GROUP BY
        if (!groupByColumns.isEmpty()) {
            query.append(" GROUP BY ")
                 .append(String.join(", ", groupByColumns));
        }

        // HAVING
        if (!havingConditions.isEmpty()) {
            query.append(" HAVING ")
                 .append(String.join(" AND ", havingConditions));
        }

        // ORDER BY
        if (!orderByColumns.isEmpty()) {
            query.append(" ORDER BY ")
                 .append(String.join(", ", orderByColumns));
        } else if (offset != null || fetch != null) {
            throw new IllegalStateException("ORDER BY required for OFFSET/FETCH");
        }

        // PAGINATION
        if (offset != null) {
            query.append(" OFFSET ").append(offset).append(" ROWS");
        }

        if (fetch != null) {
            query.append(" FETCH NEXT ").append(fetch).append(" ROWS ONLY");
        }

        return query.toString();
    }

    // ================= HELPERS =================

    private String formatValue(Object value) {
        if (value instanceof String) {
            return "'" + value + "'";
        }
        return value.toString();
    }

    // ================= SAMPLE TABLE DEFINITIONS =================

    public static class EmployeeTable extends TableImpl {
        public final Column<Integer> ID = new ColumnImpl<>("id", this);
        public final Column<String> NAME = new ColumnImpl<>("name", this);
        public final Column<Double> SALARY = new ColumnImpl<>("salary", this);
        public final Column<Integer> DEPT_ID = new ColumnImpl<>("dept_id", this);

        public EmployeeTable(String alias) {
            super("employees", alias);
        }
    }

    public static class DepartmentTable extends TableImpl {
        public final Column<Integer> ID = new ColumnImpl<>("id", this);
        public final Column<String> NAME = new ColumnImpl<>("department_name", this);

        public DepartmentTable(String alias) {
            super("departments", alias);
        }
    }

    // ================= DEMO MAIN =================

    public static void main(String[] args) {

        EmployeeTable e = new EmployeeTable("e");
        DepartmentTable d = new DepartmentTable("d");

        SqlQueryBuilder qb = new SqlQueryBuilder();

        String query = qb
                .select(e.NAME)
                .select(d.NAME)
                .sum(e.SALARY, "total_salary")
                .count(e.ID, "emp_count")

                .from(e)

                .join(JoinType.INNER, d)
                    .on(e.DEPT_ID, d.ID)
                    .endJoin()

                .where(e.SALARY, ">", 50000)

                .groupBy(e.NAME)
                .groupBy(d.NAME)

                .having("SUM(e.salary) > 100000")

                .orderByAlias("total_salary", SortOrder.DESC)

                .offset(0)
                .fetch(10)

                .build();

        System.out.println(query);
    }
}
