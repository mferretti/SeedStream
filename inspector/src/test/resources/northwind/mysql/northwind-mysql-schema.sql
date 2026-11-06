USE northwind;

CREATE TABLE category
(
    category_id   INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(30)  NOT NULL,
    description   VARCHAR(100) NOT NULL
);

CREATE TABLE customer_group_threshold
(
    customer_group_name VARCHAR(20)    NULL,
    range_bottom        DECIMAL(20, 4) NULL,
    range_top           DECIMAL(20, 4) NULL
);

CREATE TABLE customer
(
    customer_id   VARCHAR(5)  NOT NULL PRIMARY KEY,
    company_name  VARCHAR(40) NOT NULL,
    contact_name  VARCHAR(30) NULL,
    contact_title VARCHAR(30) NULL,
    address       VARCHAR(60) NULL,
    city          VARCHAR(15) NULL,
    region        VARCHAR(15) NULL,
    postal_code   VARCHAR(10) NULL,
    country       VARCHAR(15) NULL,
    phone         VARCHAR(24) NULL,
    fax           VARCHAR(24) NULL
);

CREATE TABLE employee
(
    employee_id       INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    last_name         VARCHAR(20)  NOT NULL,
    first_name        VARCHAR(10)  NOT NULL,
    title             VARCHAR(30)  NULL,
    title_of_courtesy VARCHAR(25)  NULL,
    birth_date        DATETIME     NULL,
    hire_date         DATETIME     NULL,
    address           VARCHAR(60)  NULL,
    city              VARCHAR(15)  NULL,
    region            VARCHAR(15)  NULL,
    postal_code       VARCHAR(10)  NULL,
    country           VARCHAR(15)  NULL,
    home_phone        VARCHAR(24)  NULL,
    extension         VARCHAR(4)   NULL,
    photo             VARCHAR(0)   NULL,
    notes             TEXT         NULL,
    reports_to        INT          NULL,
    photo_path        VARCHAR(255) NULL
);

create table order_detail
(
    order_id   INT            NOT NULL,
    product_id INT            NOT NULL,
    unit_price DECIMAL(20, 4) NOT NULL,
    quantity   SMALLINT       NOT NULL,
    discount   DECIMAL(20, 4) NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE `order`
(
    order_id         INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id      VARCHAR(5)     NOT NULL,
    employee_id      INT            NULL,
    order_date       DATETIME       NULL,
    required_date    DATETIME       NULL,
    shipped_date     DATETIME       NULL,
    ship_via         INT            NULL,
    freight          DECIMAL(20, 4) NULL,
    ship_name        VARCHAR(40)    NULL,
    ship_address     VARCHAR(60)    NULL,
    ship_city        VARCHAR(15)    NULL,
    ship_region      VARCHAR(15)    NULL,
    ship_postal_code VARCHAR(10)    NULL,
    ship_country     VARCHAR(15)    NULL
);

CREATE TABLE product
(
    product_id        INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_name      VARCHAR(40)    NOT NULL,
    supplier_id       INT            NULL,
    category_id       INT            NULL,
    quantity_per_unit VARCHAR(20)    NULL,
    unit_price        DECIMAL(20, 4) NULL,
    unit_in_stock     SMALLINT       NULL,
    units_on_order    SMALLINT       NULL,
    reorder_level     TINYINT        NULL,
    discontinued      TINYINT        NULL
);

CREATE TABLE shipper
(
    shipper_id   INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(40) NOT NULL,
    phone        VARCHAR(24) NULL
);

CREATE TABLE supplier
(
    supplier_id   INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_name  VARCHAR(40)  NULL,
    contact_name  VARCHAR(30)  NULL,
    contact_title VARCHAR(30)  NULL,
    address       VARCHAR(60)  NULL,
    city          VARCHAR(15)  NULL,
    region        VARCHAR(15)  NULL,
    postal_code   VARCHAR(10)  NULL,
    country       VARCHAR(15)  NULL,
    phone         VARCHAR(24)  NULL,
    Fax           VARCHAR(24)  NULL,
    home_page     VARCHAR(100) NULL
);

ALTER TABLE employee
    ADD CONSTRAINT fk_employees_employees FOREIGN KEY (reports_to)
        REFERENCES employee (employee_id);

ALTER TABLE order_detail
    ADD CONSTRAINT fk_order_details_orders FOREIGN KEY (order_id)
        REFERENCES `order` (order_id);

ALTER TABLE order_detail
    ADD CONSTRAINT fk_order_details_products FOREIGN KEY (product_id)
        REFERENCES product (product_id);

ALTER TABLE `order`
    ADD CONSTRAINT fk_orders_customers FOREIGN KEY (customer_id)
        REFERENCES customer (customer_id);

ALTER TABLE `order`
    ADD CONSTRAINT fk_orders_employees FOREIGN KEY (employee_id)
        REFERENCES employee (employee_id);

ALTER TABLE `order`
    ADD CONSTRAINT fk_orders_shippers FOREIGN KEY (ship_via)
        REFERENCES shipper (shipper_id);


ALTER TABLE product
    ADD CONSTRAINT fk_products_categories FOREIGN KEY (category_id)
        REFERENCES category (category_id);

ALTER TABLE product
    ADD CONSTRAINT fk_products_suppliers FOREIGN KEY (supplier_id)
        REFERENCES supplier (supplier_id);

