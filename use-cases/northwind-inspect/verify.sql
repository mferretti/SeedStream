-- Post-seed checks. The FK and PK constraints from schema.sql are active during the load, so
-- every inserted row already satisfies them; this script makes the result visible.
-- Run: psql -h localhost -U northwind -d northwind -f verify.sql

\echo '== Row counts'
SELECT 'categories' AS table_name, count(*) FROM categories
UNION ALL SELECT 'region', count(*) FROM region
UNION ALL SELECT 'shippers', count(*) FROM shippers
UNION ALL SELECT 'suppliers', count(*) FROM suppliers
UNION ALL SELECT 'customer_demographics', count(*) FROM customer_demographics
UNION ALL SELECT 'employees', count(*) FROM employees
UNION ALL SELECT 'territories', count(*) FROM territories
UNION ALL SELECT 'products', count(*) FROM products
UNION ALL SELECT 'employee_territories', count(*) FROM employee_territories
UNION ALL SELECT 'customers', count(*) FROM customers
UNION ALL SELECT 'customer_customer_demo', count(*) FROM customer_customer_demo
UNION ALL SELECT 'orders', count(*) FROM orders
UNION ALL SELECT 'order_details', count(*) FROM order_details;

\echo '== FK constraints enforced during the load (expect 13)'
SELECT count(*) AS foreign_keys FROM pg_constraint WHERE contype = 'f' AND connamespace = 'public'::regnamespace;

\echo '== Orphan check on the largest edges (expect 0 / 0; a NULL customer_id is not an orphan)'
SELECT
  (SELECT count(*) FROM order_details d LEFT JOIN orders o USING (order_id) WHERE o.order_id IS NULL) AS orphan_details,
  (SELECT count(*) FROM orders o LEFT JOIN customers c USING (customer_id)
     WHERE o.customer_id IS NOT NULL AND c.customer_id IS NULL) AS orphan_orders;

\echo '== Classic Northwind query: top 5 customers by revenue'
SELECT c.customer_id, c.company_name, c.country,
       round(sum(d.unit_price * d.quantity * (1 - d.discount))::numeric, 2) AS revenue
FROM customers c
JOIN orders o USING (customer_id)
JOIN order_details d USING (order_id)
GROUP BY c.customer_id, c.company_name, c.country
ORDER BY revenue DESC
LIMIT 5;

\echo '== Sales by category'
SELECT cat.category_name, count(*) AS lines,
       round(sum(d.unit_price * d.quantity)::numeric, 2) AS gross
FROM order_details d
JOIN products p USING (product_id)
JOIN categories cat USING (category_id)
GROUP BY cat.category_name
ORDER BY gross DESC;
