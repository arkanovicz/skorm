-- database republicate

-- schema republicate
DROP SCHEMA IF EXISTS republicate CASCADE;
CREATE SCHEMA republicate;
SET search_path TO republicate,public;

CREATE TABLE decision (
  id serial NOT NULL,
  message bigint NOT NULL,
  second bigint,
  outcome varchar(20) NOT NULL,
  PRIMARY KEY (id)
);


CREATE TABLE ballot (
  id serial NOT NULL,
  choice varchar(20) NOT NULL,
  decision_id integer NOT NULL,
  PRIMARY KEY (id)
);

ALTER TABLE decision ADD CONSTRAINT message FOREIGN KEY (message) REFERENCES arkad.message (id);
ALTER TABLE decision ADD CONSTRAINT second FOREIGN KEY (second) REFERENCES arkad.message (id);
ALTER TABLE ballot ADD CONSTRAINT decision FOREIGN KEY (decision_id) REFERENCES decision (id);
