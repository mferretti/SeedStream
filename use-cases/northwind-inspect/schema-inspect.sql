-- Northwind for PostgreSQL, prepared for `seedstream inspect`: schema.sql with one mechanical
-- workaround for a current inspector gap (MIT License, see LICENSE-MIT-microsoft.txt):
--   `ALTER TABLE ONLY ... ADD CONSTRAINT` PK/FK/UNIQUE moved into each table body  -> #376
-- Load schema.sql (not this file) into PostgreSQL.

--
-- PostgreSQL database dump
--

\restrict ZAzVMTi5gdD7GNni2V9iTJOyvEorVwhIhzw7V3JZRXlZISgx4OClCkhc03YNS1W

-- Dumped from database version 17.9
-- Dumped by pg_dump version 17.9

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    category_id smallint NOT NULL,
    category_name character varying(15) NOT NULL,
    description text,
    picture bytea,
    CONSTRAINT categories_pkey PRIMARY KEY (category_id)
);


--
-- Name: customer_customer_demo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_customer_demo (
    customer_id character varying(5) NOT NULL,
    customer_type_id character varying(10) NOT NULL,
    CONSTRAINT customer_customer_demo_pkey PRIMARY KEY (customer_id, customer_type_id),
    CONSTRAINT customer_customer_demo_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id),
    CONSTRAINT customer_customer_demo_customer_type_id_fkey FOREIGN KEY (customer_type_id) REFERENCES public.customer_demographics(customer_type_id)
);


--
-- Name: customer_demographics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_demographics (
    customer_type_id character varying(10) NOT NULL,
    customer_desc text,
    CONSTRAINT customer_demographics_pkey PRIMARY KEY (customer_type_id)
);


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
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
    CONSTRAINT customers_pkey PRIMARY KEY (customer_id)
);


--
-- Name: employee_territories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_territories (
    employee_id smallint NOT NULL,
    territory_id character varying(20) NOT NULL,
    CONSTRAINT employee_territories_pkey PRIMARY KEY (employee_id, territory_id),
    CONSTRAINT employee_territories_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id),
    CONSTRAINT employee_territories_territory_id_fkey FOREIGN KEY (territory_id) REFERENCES public.territories(territory_id)
);


--
-- Name: employees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employees (
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
    CONSTRAINT employees_birth_date_check CHECK ((birth_date < CURRENT_DATE)),
    CONSTRAINT employees_pkey PRIMARY KEY (employee_id),
    CONSTRAINT employees_reports_to_fkey FOREIGN KEY (reports_to) REFERENCES public.employees(employee_id)
);


--
-- Name: order_details; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_details (
    order_id smallint NOT NULL,
    product_id smallint NOT NULL,
    unit_price numeric(10,2) DEFAULT 0 NOT NULL,
    quantity smallint DEFAULT 1 NOT NULL,
    discount real DEFAULT 0 NOT NULL,
    CONSTRAINT order_details_discount_check CHECK (((discount >= (0)::double precision) AND (discount <= (1)::double precision))),
    CONSTRAINT order_details_quantity_check CHECK ((quantity > 0)),
    CONSTRAINT order_details_unit_price_check CHECK ((unit_price >= (0)::numeric)),
    CONSTRAINT order_details_pkey PRIMARY KEY (order_id, product_id),
    CONSTRAINT order_details_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(order_id),
    CONSTRAINT order_details_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id)
);


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    order_id smallint NOT NULL,
    customer_id character varying(5),
    employee_id smallint,
    order_date date,
    required_date date,
    shipped_date date,
    ship_via smallint,
    freight numeric(10,2) DEFAULT 0,
    ship_name character varying(40),
    ship_address character varying(60),
    ship_city character varying(15),
    ship_region character varying(15),
    ship_postal_code character varying(10),
    ship_country character varying(15),
    CONSTRAINT orders_pkey PRIMARY KEY (order_id),
    CONSTRAINT orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id),
    CONSTRAINT orders_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES public.employees(employee_id),
    CONSTRAINT orders_ship_via_fkey FOREIGN KEY (ship_via) REFERENCES public.shippers(shipper_id)
);


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    product_id smallint NOT NULL,
    product_name character varying(40) NOT NULL,
    supplier_id smallint,
    category_id smallint,
    quantity_per_unit character varying(20),
    unit_price numeric(10,2) DEFAULT 0,
    units_in_stock smallint DEFAULT 0,
    units_on_order smallint DEFAULT 0,
    reorder_level smallint DEFAULT 0,
    discontinued boolean DEFAULT false NOT NULL,
    CONSTRAINT products_reorder_level_check CHECK ((reorder_level >= 0)),
    CONSTRAINT products_unit_price_check CHECK ((unit_price >= (0)::numeric)),
    CONSTRAINT products_units_in_stock_check CHECK ((units_in_stock >= 0)),
    CONSTRAINT products_units_on_order_check CHECK ((units_on_order >= 0)),
    CONSTRAINT products_pkey PRIMARY KEY (product_id),
    CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(category_id),
    CONSTRAINT products_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(supplier_id)
);


--
-- Name: region; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.region (
    region_id smallint NOT NULL,
    region_description character varying(50) NOT NULL,
    CONSTRAINT region_pkey PRIMARY KEY (region_id)
);


--
-- Name: shippers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shippers (
    shipper_id smallint NOT NULL,
    company_name character varying(40) NOT NULL,
    phone character varying(24),
    CONSTRAINT shippers_pkey PRIMARY KEY (shipper_id)
);


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
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
    CONSTRAINT suppliers_pkey PRIMARY KEY (supplier_id)
);


--
-- Name: territories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.territories (
    territory_id character varying(20) NOT NULL,
    territory_description character varying(50) NOT NULL,
    region_id smallint NOT NULL,
    CONSTRAINT territories_pkey PRIMARY KEY (territory_id),
    CONSTRAINT territories_region_id_fkey FOREIGN KEY (region_id) REFERENCES public.region(region_id)
);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: customer_customer_demo customer_customer_demo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: customer_demographics customer_demographics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: employee_territories employee_territories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: employees employees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: order_details order_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: region region_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: shippers shippers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: territories territories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: category_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX category_name ON public.categories USING btree (category_name);


--
-- Name: customers_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customers_city ON public.customers USING btree (city);


--
-- Name: customers_company_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customers_company_name ON public.customers USING btree (company_name);


--
-- Name: employees_last_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employees_last_name ON public.employees USING btree (last_name);


--
-- Name: orders_order_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX orders_order_date ON public.orders USING btree (order_date);


--
-- Name: products_product_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX products_product_name ON public.products USING btree (product_name);


--
-- Name: customer_customer_demo customer_customer_demo_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: customer_customer_demo customer_customer_demo_customer_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: employee_territories employee_territories_employee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: employee_territories employee_territories_territory_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: employees employees_reports_to_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: order_details order_details_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: order_details order_details_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: orders orders_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: orders orders_employee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: orders orders_ship_via_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: products products_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: products products_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- Name: territories territories_region_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--



--
-- PostgreSQL database dump complete
--

\unrestrict ZAzVMTi5gdD7GNni2V9iTJOyvEorVwhIhzw7V3JZRXlZISgx4OClCkhc03YNS1W

