CREATE TABLE customers (
  id         BIGINT PRIMARY KEY,
  email      VARCHAR(255) NOT NULL,
  full_name  VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE orders (
  id          BIGINT PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customers(id),
  amount      NUMERIC(10,2) NOT NULL,
  status      VARCHAR(16) NOT NULL CHECK (status IN ('new','paid','shipped','cancelled'))
);

CREATE TABLE tags (
  id   BIGINT PRIMARY KEY,
  name VARCHAR(32) NOT NULL
);

CREATE TABLE order_tags (
  order_id BIGINT NOT NULL REFERENCES orders(id),
  tag_id   BIGINT NOT NULL REFERENCES tags(id),
  PRIMARY KEY (order_id, tag_id)
);
