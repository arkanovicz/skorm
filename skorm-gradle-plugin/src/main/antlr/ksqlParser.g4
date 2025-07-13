parser grammar ksqlParser;

options { tokenVocab = ksqlLexer; }

database: DATABASE name=LABEL LC ( schema )* RC ;

schema: SCHEMA name=LABEL LC ( item )* RC ;

item:
  attr_type=ATTR ( receiver=LABEL DOT )? name=LABEL ( LP arguments RP )? FS type ( qualifier )? EQ sql_spec
| attr_type=MUTATION ( receiver=LABEL DOT )? name=LABEL ( LP arguments RP )? EQ sql_spec
;

sql_spec:
  query=single_query
| queries=multiple_queries
;

arguments: ( argument ( CM argument )* ) ;

argument: LABEL ( FS simple_type )? ;

type: simple_type | json_object_type | out_entity | complex_type ;

simple_type:
  BOOLEAN
| INT
| LONG
| FLOAT
| DOUBLE
| TIME
| DATE
| DATETIME
| INTERVAL
| CHAR
| STRING
| JSON
;

json_object_type: JSON DOT OBJECT ;

out_entity: LABEL ;

complex_type: complex_type_spec | LP complex_type_spec RP ;

complex_type_spec: ( entity=LABEL | field=LABEL FS simple_type ) ( CM field=LABEL FS simple_type )+ ;

qualifier: ( optional=QM | mmultiple=ST ) ;

single_query: sql SC ;
multiple_queries: LC ( sql SC )* RC ;
sql: QUERY_PART+ ;
