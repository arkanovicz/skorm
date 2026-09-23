-- database shapes

-- schema main
DROP SCHEMA IF EXISTS main CASCADE;
CREATE SCHEMA main;
SET search_path TO main,public;
CREATE TYPE enum_level AS ENUM ('low','high');
CREATE CAST (varchar AS enum_level) WITH INOUT AS IMPLICIT;
CREATE TYPE enum_person_kind AS ENUM ('person','vip');
CREATE CAST (varchar AS enum_person_kind) WITH INOUT AS IMPLICIT;
CREATE TYPE enum_nature AS ENUM ('a','b');
CREATE CAST (varchar AS enum_nature) WITH INOUT AS IMPLICIT;
CREATE TABLE person (
  person_id serial NOT NULL,
  name varchar(50) NOT NULL,
  rank enum_level NOT NULL,
  nature enum_nature NOT NULL,
  small smallint NOT NULL,
  tiny smallint,
  big biginteger,
  uid uuid,
  meta json,
  born date,
  seen timestamp,
  kind enum_person_kind NOT NULL DEFAULT 'person',
  PRIMARY KEY (person_id)
);


CREATE TABLE country (
  code char(2) NOT NULL,
  label varchar(50) NOT NULL,
  PRIMARY KEY (code)
);


CREATE TABLE friendship (
  a_id integer NOT NULL,
  b_id integer NOT NULL,
  since date NOT NULL,
  PRIMARY KEY (a_id, b_id)
);


CREATE TABLE gift (
  label varchar(20) NOT NULL,
  a_id integer NOT NULL,
  b_id integer NOT NULL
);


CREATE TABLE address (
  address_id serial NOT NULL,
  city varchar(50) NOT NULL,
  owner integer NOT NULL,
  backup integer,
  code char(2) NOT NULL,
  PRIMARY KEY (address_id)
);


CREATE TABLE base_vip (
  person_id serial NOT NULL,
  PRIMARY KEY (person_id)
);

CREATE VIEW vip AS
  SELECT
    person.person_id,name,rank,nature,small,tiny,big,uid,meta,born,seen,kind
  FROM base_vip JOIN person ON person.person_id = base_vip.person_id;

CREATE RULE insert_vip AS ON INSERT TO vip DO INSTEAD (
  INSERT INTO person (person_id,name,rank,nature,small,tiny,big,uid,meta,born,seen,kind)
    VALUES (     COALESCE(NEW.person_id,NEXTVAL('person_person_id_seq')),NEW.name,NEW.rank,NEW.nature,NEW.small,NEW.tiny,NEW.big,NEW.uid,NEW.meta,NEW.born,NEW.seen,'vip');
  SELECT SETVAL('person_person_id_seq', NEW.person_id) person_id WHERE NEW.person_id > (SELECT last_value FROM person_person_id_seq);
  INSERT INTO base_vip (person_id)
    VALUES (COALESCE(NEW.person_id,CURRVAL('person_person_id_seq')))
  RETURNING base_vip.person_id,
            (SELECT name FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT rank FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT nature FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT small FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT tiny FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT big FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT uid FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT meta FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT born FROM person WHERE person.person_id = base_vip.person_id),
            (SELECT seen FROM person WHERE person.person_id = base_vip.person_id),
            'vip'::enum_person_kind;
);

CREATE RULE update_vip AS ON UPDATE TO vip DO INSTEAD (
  UPDATE person
    SET name = NEW.name,rank = NEW.rank,nature = NEW.nature,small = NEW.small,tiny = NEW.tiny,big = NEW.big,uid = NEW.uid,meta = NEW.meta,born = NEW.born,seen = NEW.seen
    WHERE person_id = NEW.person_id
  RETURNING NEW.*;
);

CREATE RULE delete_vip AS ON DELETE TO vip DO INSTEAD (
  DELETE FROM person WHERE person_id = OLD.person_id;
);


CREATE TABLE person_address (
  person_id integer NOT NULL,
  address_id integer NOT NULL
);

ALTER TABLE friendship ADD CONSTRAINT a FOREIGN KEY (a_id) REFERENCES person (person_id);
ALTER TABLE friendship ADD CONSTRAINT b FOREIGN KEY (b_id) REFERENCES person (person_id);
ALTER TABLE gift ADD CONSTRAINT a_id_b FOREIGN KEY (a_id,b_id) REFERENCES friendship (a_id,b_id);
ALTER TABLE address ADD CONSTRAINT owner FOREIGN KEY (owner) REFERENCES person (person_id);
ALTER TABLE address ADD CONSTRAINT backup FOREIGN KEY (backup) REFERENCES person (person_id);
ALTER TABLE address ADD CONSTRAINT code FOREIGN KEY (code) REFERENCES country (code);
ALTER TABLE person_address ADD CONSTRAINT person FOREIGN KEY (person_id) REFERENCES person (person_id) ON DELETE CASCADE;
ALTER TABLE person_address ADD CONSTRAINT address FOREIGN KEY (address_id) REFERENCES address (address_id) ON DELETE CASCADE;
ALTER TABLE base_vip ADD CONSTRAINT person FOREIGN KEY (person_id) REFERENCES person (person_id) ON DELETE CASCADE;



-- schema other
DROP SCHEMA IF EXISTS other CASCADE;
CREATE SCHEMA other;
SET search_path TO other,public;

CREATE TABLE badge (
  label varchar(20) NOT NULL,
  person_id integer NOT NULL
);

ALTER TABLE badge ADD CONSTRAINT person FOREIGN KEY (person_id) REFERENCES main.person (person_id);
