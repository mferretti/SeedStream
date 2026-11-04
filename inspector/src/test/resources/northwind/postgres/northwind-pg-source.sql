-- Northwind for PostgreSQL — SeedStream's own conversion of Microsoft's instnwnd.sql
-- (https://github.com/microsoft/sql-server-samples, samples/databases/northwind-pubs, MIT License).
-- Converted by hand: snake_case names, PostgreSQL types (image -> bytea, ntext -> text,
-- money -> numeric(10,2), bit -> boolean, datetime -> date). Keys are smallint on purpose
-- (Northwind's tables are tiny), text keys keep their nchar widths as varchar.
-- CHECK constraints are carried over from the original.

CREATE TABLE categories (
    category_id smallint NOT NULL PRIMARY KEY,
    category_name varchar(15) NOT NULL,
    description text,
    picture bytea
);
CREATE INDEX category_name ON categories (category_name);

CREATE TABLE customers (
    customer_id varchar(5) NOT NULL PRIMARY KEY,
    company_name varchar(40) NOT NULL,
    contact_name varchar(30),
    contact_title varchar(30),
    address varchar(60),
    city varchar(15),
    region varchar(15),
    postal_code varchar(10),
    country varchar(15),
    phone varchar(24),
    fax varchar(24)
);
CREATE INDEX customers_city ON customers (city);
CREATE INDEX customers_company_name ON customers (company_name);

CREATE TABLE customer_demographics (
    customer_type_id varchar(10) NOT NULL PRIMARY KEY,
    customer_desc text
);

CREATE TABLE customer_customer_demo (
    customer_id varchar(5) NOT NULL REFERENCES customers (customer_id),
    customer_type_id varchar(10) NOT NULL REFERENCES customer_demographics (customer_type_id),
    PRIMARY KEY (customer_id, customer_type_id)
);

CREATE TABLE employees (
    employee_id smallint NOT NULL PRIMARY KEY,
    last_name varchar(20) NOT NULL,
    first_name varchar(10) NOT NULL,
    title varchar(30),
    title_of_courtesy varchar(25),
    birth_date date CHECK (birth_date < CURRENT_DATE),
    hire_date date,
    address varchar(60),
    city varchar(15),
    region varchar(15),
    postal_code varchar(10),
    country varchar(15),
    home_phone varchar(24),
    extension varchar(4),
    photo bytea,
    notes text,
    reports_to smallint REFERENCES employees (employee_id),
    photo_path varchar(255)
);
CREATE INDEX employees_last_name ON employees (last_name);

CREATE TABLE region (
    region_id smallint NOT NULL PRIMARY KEY,
    region_description varchar(50) NOT NULL
);

CREATE TABLE territories (
    territory_id varchar(20) NOT NULL PRIMARY KEY,
    territory_description varchar(50) NOT NULL,
    region_id smallint NOT NULL REFERENCES region (region_id)
);

CREATE TABLE employee_territories (
    employee_id smallint NOT NULL REFERENCES employees (employee_id),
    territory_id varchar(20) NOT NULL REFERENCES territories (territory_id),
    PRIMARY KEY (employee_id, territory_id)
);

CREATE TABLE shippers (
    shipper_id smallint NOT NULL PRIMARY KEY,
    company_name varchar(40) NOT NULL,
    phone varchar(24)
);

CREATE TABLE suppliers (
    supplier_id smallint NOT NULL PRIMARY KEY,
    company_name varchar(40) NOT NULL,
    contact_name varchar(30),
    contact_title varchar(30),
    address varchar(60),
    city varchar(15),
    region varchar(15),
    postal_code varchar(10),
    country varchar(15),
    phone varchar(24),
    fax varchar(24),
    homepage text
);

CREATE TABLE products (
    product_id smallint NOT NULL PRIMARY KEY,
    product_name varchar(40) NOT NULL,
    supplier_id smallint REFERENCES suppliers (supplier_id),
    category_id smallint REFERENCES categories (category_id),
    quantity_per_unit varchar(20),
    unit_price numeric(10,2) DEFAULT 0 CHECK (unit_price >= 0),
    units_in_stock smallint DEFAULT 0 CHECK (units_in_stock >= 0),
    units_on_order smallint DEFAULT 0 CHECK (units_on_order >= 0),
    reorder_level smallint DEFAULT 0 CHECK (reorder_level >= 0),
    discontinued boolean NOT NULL DEFAULT false
);
CREATE INDEX products_product_name ON products (product_name);

CREATE TABLE orders (
    order_id smallint NOT NULL PRIMARY KEY,
    customer_id varchar(5) REFERENCES customers (customer_id),
    employee_id smallint REFERENCES employees (employee_id),
    order_date date,
    required_date date,
    shipped_date date,
    ship_via smallint REFERENCES shippers (shipper_id),
    freight numeric(10,2) DEFAULT 0,
    ship_name varchar(40),
    ship_address varchar(60),
    ship_city varchar(15),
    ship_region varchar(15),
    ship_postal_code varchar(10),
    ship_country varchar(15)
);
CREATE INDEX orders_order_date ON orders (order_date);

CREATE TABLE order_details (
    order_id smallint NOT NULL REFERENCES orders (order_id),
    product_id smallint NOT NULL REFERENCES products (product_id),
    unit_price numeric(10,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
    quantity smallint NOT NULL DEFAULT 1 CHECK (quantity > 0),
    discount real NOT NULL DEFAULT 0 CHECK (discount >= 0 AND discount <= 1),
    PRIMARY KEY (order_id, product_id)
);
