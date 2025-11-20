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
DOUBLE: 'Double' ;
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
ARROW: '->' -> pushMode( BLOCK_OR_QUERY );
LC: '{' ;
RC: '}' ;
FS: ':' ;
LP: '(' ;
RP: ')' ;
DOT: '.' ;
ST: '*' ;
QM: '?' ;
CM: ',' ;
EQ: '='  -> pushMode( QUERY );
SC: ';' ;

// whitespaces and comments
WS: [ \t\n\r]+ -> skip ;
LINE_COMMENT : '//' .*? '\n' -> skip ;
BLOCK_COMMENT:'/*' .*? '*/' -> skip ;

mode BLOCK_OR_QUERY ;
WS2: [ \t\n\r]+ -> skip ;
BLOCK_START: '{' -> type( LC ), pushMode( QUERY ) ;
BLOCK_END: '}' -> type( RC ), popMode ;
QUERY_START: . -> more, mode( QUERY ) ;

mode QUERY ;
WS3: [ \t\n\r]+ -> more ;
PARAM_START: '{' -> more, pushMode( PARAM ) ;
OPENING_SQ: '\'' -> more, pushMode( QUOTED ) ;
OPENING_DQ: '"' -> more, pushMode( DOUBLE_QUOTED ) ;
BEGIN: ( 'begin' | 'BEGIN' ) -> more, pushMode( SCOPED ) ;
QUERY_END: ';' -> type( SC ), popMode ;
QUERY_PART: ~( [{\\'";] )+ -> type( QUERY_PART ) ;

mode PARAM ;
PARAM_END: '}' -> type( QUERY_PART ), popMode ;
PARAM_NAME: [a-z_][a-z0-9_]+ -> more ;

mode QUOTED ;
ESCAPED_SQ: '\\\'' -> more ;
CLOSING_SQ: '\'' -> type( QUERY_PART ), popMode ;
QUOTED_STRING: .+? -> more ;

mode DOUBLE_QUOTED ;
ESCAPED_DQ: '\\"' -> more ;
CLOSING_DQ: '"' -> type( QUERY_PART ), popMode ;
DOUBLE_QUOTED_STRING: .+? -> more ;

mode SCOPED ;
WS4: [ \t\n\r]+ -> more ;
PARAM_START2: '{' -> more, pushMode( PARAM ) ;
OPENING_SQ2: '\'' -> more, pushMode( QUOTED ) ;
OPENING_DQ2: '"' -> more, pushMode( DOUBLE_QUOTED ) ;
BEGIN2: ( 'begin' | 'BEGIN' ) -> more, pushMode( SCOPED ) ;
END: ( 'end' | 'END' ) -> type( QUERY_PART ), popMode ;
SCOPED_STRING: .+? -> more ;
