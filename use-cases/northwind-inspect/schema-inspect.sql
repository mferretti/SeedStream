-- Northwind (PostgreSQL port), prepared for `seedstream inspect`.
-- Source: https://github.com/pthom/northwind_psql (Ms-PL, see LICENSE-northwind.txt).
-- (Comment wording avoids the words of a table definition: #443.)
-- Derived from schema.sql with three mechanical workarounds for current inspector gaps:
--   1. runs of empty lines squeezed to one (`cat -s`)                              -> #436
--   2. `ALTER TABLE ONLY t ADD CONSTRAINT ...` PK/FK moved into each table body     -> #376
--   3. `REFERENCES t` spelled out as `REFERENCES t (pk_column)`                    -> #437
-- Load schema.sql (not this file) into PostgreSQL.
--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

SET default_tablespace = '';

SET default_with_oids = false;

---
--- drop tables
---

DROP TABLE IF EXISTS customer_customer_demo;
DROP TABLE IF EXISTS customer_demographics;
DROP TABLE IF EXISTS employee_territories;
DROP TABLE IF EXISTS order_details;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS shippers;
DROP TABLE IF EXISTS suppliers;
DROP TABLE IF EXISTS territories;
DROP TABLE IF EXISTS us_states;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS region;
DROP TABLE IF EXISTS employees;

--
-- Name: categories; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE categories (
    category_id smallint NOT NULL,
    category_name character varying(15) NOT NULL,
    description text,
    picture bytea,
    CONSTRAINT pk_categories PRIMARY KEY (category_id)
);

--
-- Name: customer_customer_demo; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE customer_customer_demo (
    customer_id character varying(5) NOT NULL,
    customer_type_id character varying(5) NOT NULL,
    CONSTRAINT pk_customer_customer_demo PRIMARY KEY (customer_id, customer_type_id),
    CONSTRAINT fk_customer_customer_demo_customer_demographics FOREIGN KEY (customer_type_id) REFERENCES customer_demographics (customer_type_id),
    CONSTRAINT fk_customer_customer_demo_customers FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
);

--
-- Name: customer_demographics; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE customer_demographics (
    customer_type_id character varying(5) NOT NULL,
    customer_desc text,
    CONSTRAINT pk_customer_demographics PRIMARY KEY (customer_type_id)
);

--
-- Name: customers; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE customers (
    customer_id character varying(5) NOT NULL,
    company_name character varying(40) NOT NULL,
    contact_name character varying(30),
    contact_title character varying(30),
    address character varying(60),
    city character varying(15),
    region character varying(15),
    postal_code character varying(10),
    country character varying(15),
    phone character varying(24),
    fax character varying(24),
    CONSTRAINT pk_customers PRIMARY KEY (customer_id)
);

--
-- Name: employees; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE employees (
    employee_id smallint NOT NULL,
    last_name character varying(20) NOT NULL,
    first_name character varying(10) NOT NULL,
    title character varying(30),
    title_of_courtesy character varying(25),
    birth_date date,
    hire_date date,
    address character varying(60),
    city character varying(15),
    region character varying(15),
    postal_code character varying(10),
    country character varying(15),
    home_phone character varying(24),
    extension character varying(4),
    photo bytea,
    notes text,
    reports_to smallint,
    photo_path character varying(255),
    CONSTRAINT pk_employees PRIMARY KEY (employee_id),
    CONSTRAINT fk_employees_employees FOREIGN KEY (reports_to) REFERENCES employees (employee_id)
);

--
-- Name: employee_territories; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE employee_territories (
    employee_id smallint NOT NULL,
    territory_id character varying(20) NOT NULL,
    CONSTRAINT pk_employee_territories PRIMARY KEY (employee_id, territory_id),
    CONSTRAINT fk_employee_territories_territories FOREIGN KEY (territory_id) REFERENCES territories (territory_id),
    CONSTRAINT fk_employee_territories_employees FOREIGN KEY (employee_id) REFERENCES employees (employee_id)
);

--
-- Name: order_details; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE order_details (
    order_id smallint NOT NULL,
    product_id smallint NOT NULL,
    unit_price real NOT NULL,
    quantity smallint NOT NULL,
    discount real NOT NULL,
    CONSTRAINT pk_order_details PRIMARY KEY (order_id, product_id),
    CONSTRAINT fk_order_details_products FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_order_details_orders FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

--
-- Name: orders; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE orders (
    order_id smallint NOT NULL,
    customer_id character varying(5),
    employee_id smallint,
    order_date date,
    required_date date,
    shipped_date date,
    ship_via smallint,
    freight real,
    ship_name character varying(40),
    ship_address character varying(60),
    ship_city character varying(15),
    ship_region character varying(15),
    ship_postal_code character varying(10),
    ship_country character varying(15),
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT fk_orders_customers FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT fk_orders_employees FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_orders_shippers FOREIGN KEY (ship_via) REFERENCES shippers (shipper_id)
);

--
-- Name: products; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE products (
    product_id smallint NOT NULL,
    product_name character varying(40) NOT NULL,
    supplier_id smallint,
    category_id smallint,
    quantity_per_unit character varying(20),
    unit_price real,
    units_in_stock smallint,
    units_on_order smallint,
    reorder_level smallint,
    discontinued integer NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (product_id),
    CONSTRAINT fk_products_categories FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT fk_products_suppliers FOREIGN KEY (supplier_id) REFERENCES suppliers (supplier_id)
);

--
-- Name: region; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE region (
    region_id smallint NOT NULL,
    region_description character varying(60) NOT NULL,
    CONSTRAINT pk_region PRIMARY KEY (region_id)
);

--
-- Name: shippers; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE shippers (
    shipper_id smallint NOT NULL,
    company_name character varying(40) NOT NULL,
    phone character varying(24),
    CONSTRAINT pk_shippers PRIMARY KEY (shipper_id)
);

--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE suppliers (
    supplier_id smallint NOT NULL,
    company_name character varying(40) NOT NULL,
    contact_name character varying(30),
    contact_title character varying(30),
    address character varying(60),
    city character varying(15),
    region character varying(15),
    postal_code character varying(10),
    country character varying(15),
    phone character varying(24),
    fax character varying(24),
    homepage text,
    CONSTRAINT pk_suppliers PRIMARY KEY (supplier_id)
);

--
-- Name: territories; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE territories (
    territory_id character varying(20) NOT NULL,
    territory_description character varying(60) NOT NULL,
    region_id smallint NOT NULL,
    CONSTRAINT pk_territories PRIMARY KEY (territory_id),
    CONSTRAINT fk_territories_region FOREIGN KEY (region_id) REFERENCES region (region_id)
);

--
-- Name: us_states; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE us_states (
    state_id smallint NOT NULL,
    state_name character varying(100),
    state_abbr character varying(2),
    state_region character varying(50),
    CONSTRAINT pk_usstates PRIMARY KEY (state_id)
);

--
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: customer_customer_demo; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: customer_demographics; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: customers; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: employees; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: employee_territories; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: order_details; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: products; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: region; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: shippers; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: suppliers; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: territories; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: us_states; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Name: pk_categories; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_customer_customer_demo; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_customer_demographics; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_customers; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_employees; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_employee_territories; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_order_details; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_orders; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_products; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_region; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_shippers; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_suppliers; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_territories; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: pk_usstates; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--


--
-- Name: fk_orders_customers; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_orders_employees; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_orders_shippers; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_order_details_products; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_order_details_orders; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_products_categories; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_products_suppliers; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_territories_region; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_employee_territories_territories; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_employee_territories_employees; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_customer_customer_demo_customer_demographics; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_customer_customer_demo_customers; Type: Constraint; Schema: -; Owner: -
--


--
-- Name: fk_employees_employees; Type: Constraint; Schema: -; Owner: -
--


    
--
-- PostgreSQL database dump complete
--

