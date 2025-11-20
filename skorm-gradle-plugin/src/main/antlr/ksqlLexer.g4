lexer grammar ksqlLexer;

tokens { QUERY_PART }

// symbols
DATABASE: 'database' ;
SCHEMA: 'schema' ;
ATTR: 'attr' ;
MUTATION: 'mut' | 'mutation';

// types
BOOLEAN: 'Boolean' ;
INT: 'Int' ;
LONG: 'Long' ;
FLOAT: 'Float' ;
DOUBLE:- 'Double' ;
TIME: 'LocalTime' ;
DATE: 'LocalDate' ;
DATETIME: 'LocalDateTime' ;
INTERVAL: 'DateTimePeriod' ;
CHAR: 'Char' ;
STRING: 'String' ;
JSON: 'Json' ;
OBJECT: 'Object' ;

// values
LABEL: [a-zA-Z_][a-zA-Z0-9_]* ;

// symbols
ARROW: '->' -> pushMode( BLOCK_OR_QUERYP );
LC: '{' ;
RC: '}' ;
FS: ':' ;
LP: '(' ;
RP: ')' ;
DOT: '.' ;
S