-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  UNIQUE (type, code)
);


CREATE TABLE customer (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL UNIQUE,
name varchar(120) NOT NULL,
contact varchar(200) NOT NULL,
notes varchar(1000) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE vehicle (
id bigint AUTO_INCREMENT PRIMARY KEY,
plate varchar(60) NOT NULL UNIQUE,
vin varchar(60) NOT NULL,
model varchar(120) NOT NULL,
customer_id bigint NOT NULL,
department_id bigint NOT NULL,
odometer bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(customer_id) REFERENCES customer(id),
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE part (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL UNIQUE,
name varchar(120) NOT NULL,
unit varchar(20) NOT NULL,
department_id bigint NOT NULL,
price decimal(18,2) NOT NULL,
quantity decimal(18,3) NOT NULL,
inventory_value decimal(18,2) NOT NULL,
reorder_level decimal(18,3) NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(quantity>=0 AND inventory_value>=0 AND price>=0 AND reorder_level>=0)
);

CREATE TABLE labor_item (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL UNIQUE,
name varchar(120) NOT NULL,
unit varchar(20) NOT NULL,
price decimal(18,2) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE repair_job (
id bigint AUTO_INCREMENT PRIMARY KEY,
number varchar(60) NOT NULL UNIQUE,
vehicle_id bigint NOT NULL,
customer_id bigint NOT NULL,
plate varchar(60) NOT NULL,
model varchar(120) NOT NULL,
customer_name varchar(120) NOT NULL,
department_id bigint NOT NULL,
technician_id bigint,
odometer bigint NOT NULL,
complaint varchar(1000) NOT NULL,
intake_note varchar(1000) NOT NULL,
status varchar(20) NOT NULL,
currency varchar(3) NOT NULL,
created_at timestamp(6) NOT NULL,
created_by varchar(60) NOT NULL,
quality_note varchar(1000) NOT NULL,
handover_reference varchar(200) NOT NULL,
discount decimal(18,2) NOT NULL,
net_paid decimal(18,2) NOT NULL,
FOREIGN KEY(vehicle_id) REFERENCES vehicle(id),
FOREIGN KEY(customer_id) REFERENCES customer(id),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(technician_id) REFERENCES account(id)
);

CREATE TABLE estimate (
id bigint AUTO_INCREMENT PRIMARY KEY,
job_id bigint NOT NULL,
number varchar(60) NOT NULL UNIQUE,
status varchar(20) NOT NULL,
approval_reference varchar(200) NOT NULL,
note varchar(1000) NOT NULL,
created_at timestamp(6) NOT NULL,
approved_at timestamp(6),
FOREIGN KEY(job_id) REFERENCES repair_job(id)
);

CREATE TABLE repair_line (
id bigint AUTO_INCREMENT PRIMARY KEY,
estimate_id bigint NOT NULL,
job_id bigint NOT NULL,
kind varchar(20) NOT NULL,
item_id bigint NOT NULL,
name varchar(120) NOT NULL,
unit varchar(20) NOT NULL,
quantity decimal(18,3) NOT NULL,
price decimal(18,2) NOT NULL,
issued decimal(18,3) NOT NULL,
returned decimal(18,3) NOT NULL,
completed boolean NOT NULL,
work_note varchar(500) NOT NULL,
FOREIGN KEY(estimate_id) REFERENCES estimate(id),
FOREIGN KEY(job_id) REFERENCES repair_job(id),
CHECK(quantity>0 AND price>=0 AND issued>=0 AND returned>=0 AND returned<=issued AND issued-returned<=quantity)
);

CREATE TABLE part_movement (
id bigint AUTO_INCREMENT PRIMARY KEY,
part_id bigint NOT NULL,
department_id bigint NOT NULL,
job_id bigint,
line_id bigint,
source_id bigint,
kind varchar(20) NOT NULL,
quantity decimal(18,3) NOT NULL,
inventory_value decimal(18,2) NOT NULL,
reference varchar(120) NOT NULL,
note varchar(500) NOT NULL,
created_at timestamp(6) NOT NULL,
created_by varchar(60) NOT NULL,
FOREIGN KEY(part_id) REFERENCES part(id),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(job_id) REFERENCES repair_job(id),
FOREIGN KEY(line_id) REFERENCES repair_line(id),
FOREIGN KEY(source_id) REFERENCES part_movement(id)
);

CREATE TABLE payment_entry (
id bigint AUTO_INCREMENT PRIMARY KEY,
job_id bigint NOT NULL,
reversal_of bigint UNIQUE,
amount decimal(18,2) NOT NULL,
reference varchar(120) NOT NULL,
note varchar(500) NOT NULL,
created_at timestamp(6) NOT NULL,
created_by varchar(60) NOT NULL,
FOREIGN KEY(job_id) REFERENCES repair_job(id),
FOREIGN KEY(reversal_of) REFERENCES payment_entry(id),
CHECK(amount>0)
);

CREATE TABLE mutation_stamp (id bigint AUTO_INCREMENT PRIMARY KEY, resource varchar(80) NOT NULL, request_key varchar(80) NOT NULL, fingerprint varchar(64) NOT NULL, UNIQUE(resource,request_key));
CREATE INDEX ix_job_department_status ON repair_job(department_id,status);
CREATE INDEX ix_job_vehicle_time ON repair_job(vehicle_id,created_at);
CREATE INDEX ix_estimate_job ON estimate(job_id);
CREATE INDEX ix_line_job ON repair_line(job_id);
CREATE INDEX ix_movement_part ON part_movement(part_id,created_at);
CREATE INDEX ix_payment_job ON payment_entry(job_id);
CREATE INDEX ix_audit_department_time ON audit_event(department_id,created_at);
