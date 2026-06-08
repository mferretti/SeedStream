-- Library database schema
-- Run in order: base tables first, then dependent tables.
-- Usage: psql -U dbuser -d librarydb -f library_schema.sql

-- Drop in reverse dependency order
DROP TABLE IF EXISTS lib_supplies;
DROP TABLE IF EXISTS lib_magazine_sales;
DROP TABLE IF EXISTS lib_book_sales;
DROP TABLE IF EXISTS lib_magazines;
DROP TABLE IF EXISTS lib_books;
DROP TABLE IF EXISTS lib_publications;
DROP TABLE IF EXISTS lib_authors;

-- Base tables

CREATE TABLE lib_authors (
    id            INT PRIMARY KEY,
    first_name    VARCHAR(255)  NOT NULL,
    last_name     VARCHAR(255)  NOT NULL,
    birthdate     DATE,
    nationality   VARCHAR(255),
    email         VARCHAR(255)
);

CREATE TABLE lib_publications (
    id            INT PRIMARY KEY,
    name          VARCHAR(255)  NOT NULL,
    founded_year  INT,
    country       VARCHAR(255),
    website       VARCHAR(500)
);

-- Dependent: both FKs to base tables

CREATE TABLE lib_books (
    id             INT PRIMARY KEY,
    title          TEXT          NOT NULL,
    isbn           VARCHAR(20),
    genre          VARCHAR(20),
    pages          INT,
    language       VARCHAR(10),
    price          NUMERIC(10, 2),
    published_date DATE,
    author_id      INT           NOT NULL REFERENCES lib_authors(id),
    publisher_id   INT           NOT NULL REFERENCES lib_publications(id)
);

CREATE TABLE lib_magazines (
    id           INT PRIMARY KEY,
    title        TEXT          NOT NULL,
    issn         VARCHAR(10),
    frequency    VARCHAR(20),
    cover_price  NUMERIC(10, 2),
    category     VARCHAR(20),
    publisher_id INT           NOT NULL REFERENCES lib_publications(id)
);

-- Transaction tables: FK to books/magazines

CREATE TABLE lib_book_sales (
    id            INT PRIMARY KEY,
    sale_date     DATE,
    quantity      INT,
    unit_price    NUMERIC(10, 2),
    channel       VARCHAR(20),
    customer_name VARCHAR(255),
    book_id       INT           NOT NULL REFERENCES lib_books(id)
);

CREATE TABLE lib_magazine_sales (
    id            INT PRIMARY KEY,
    sale_date     DATE,
    issue_number  INT,
    quantity      INT,
    unit_price    NUMERIC(10, 2),
    channel       VARCHAR(20),
    customer_name VARCHAR(255),
    magazine_id   INT           NOT NULL REFERENCES lib_magazines(id)
);

-- Supply: two FKs (book + publisher)

CREATE TABLE lib_supplies (
    id             INT PRIMARY KEY,
    supply_date    DATE,
    quantity       INT,
    cost_per_unit  NUMERIC(10, 2),
    supplier_name  VARCHAR(255),
    status         VARCHAR(20),
    book_id        INT           NOT NULL REFERENCES lib_books(id),
    publisher_id   INT           NOT NULL REFERENCES lib_publications(id)
);
