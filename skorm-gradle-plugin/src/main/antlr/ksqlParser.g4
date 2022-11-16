parser grammar ksqlParser;

options { tokenVocab = ksqlLexer; }

database: DATABASE name=LABEL LC ( schema )* RC ;

schema: SCHEMA name=LABEL LC ( item )* RC ;

item:
  name=LABEL ( LP arguments RP )? FS type ( qualifier )? EQ query=single_query
| name=LABEL ( LP arguments RP )? ARROW ( query=single_query | queries=multiple_queries )
| receiver=LABEL DOT name=LABEL ( LP arguments RP )? FS type ( qualifier )? EQ query=single_query
| receiver=LABEL DOT name=LABEL ( LP arguments RP )? ARROW ( query=single_query | queries=multiple_queries )
;

arguments: ( argument ( CM argument )* ) ;

argument: LABEL ( FS simple_type )? ;

type: simple_type | out_entity | complex_type ;

simple_type:
  BOOLEAN
| INT
| LONG
| FLOAT
| DOUBLE
| TIME
| DATE
| DATETIME
| INSTANT
| INTERVAL
| CHAR
| STRING
| JSON
;

out_entity: LABEL ;

complex_type: LP ( entity=LABEL | field=LABEL FS simple_type ) ( CM field=LABEL FS simple_type )* RP ;

qualifier: ( optional=QM | mmultiple=ST ) ;

single_query: sql SC ;
multiple_queries: LC ( sql SC )* RC ;
sql: QUERY_PART+ ;
