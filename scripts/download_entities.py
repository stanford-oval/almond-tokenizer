#!/usr/bin/python3

import MySQLdb
import MySQLdb.cursors
import urllib.parse
import os
import sys
import json
import sys
import socket
import re

ABBREVIATIONS = [
    ['ltd', 'ltd.', 'limited'],
    ['corp', 'corp.', 'corporation'],
    ['l.l.c', 'llc'],
    ['&', 'and'],
    ['inc.', 'inc', 'incorporated'],
]
PROCESSED_ABBREVIATIONS = {}
for abbr in ABBREVIATIONS:
    for variant in abbr:
        PROCESSED_ABBREVIATIONS[variant] = abbr

IGNORABLE_TOKENS = {
    'sportradar': { 'fc', 'ac', 'us', 'if', 'as', 'rc', 'rb', 'il', 'fk', 'cd', 'cf' },
    'imgflip:meme_id': { 'the', },
    'tt:currency_code': { 'us' },
    'tt:stock_id': { 'l.p.', 's.a.', 'plc', 'n.v', 's.a.b', 'c.v.' }
}
PRIORITY = {
    'tt:country': 2
}
OVERRIDABLE_TYPES = {
    'sportradar': { 'ORGANIZATION' },
    'tt:stock_id': { 'ORGANIZATION' },
    'tt:country': { 'LOCATION' },
    'tt:currency_code': { 'LOCATION' }
}
NUMBER_PATTERN = re.compile('^[0-9]+$')

def process_tokens(entity_type, tokens):
    yield '('
    in_paren = False
    for token in tokens:
        if token in PROCESSED_ABBREVIATIONS:
            yield '('
            first = True
            for variant in PROCESSED_ABBREVIATIONS[token]:
                if not first:
                    yield '|'
                first = False
                yield '"' + variant + '"'
            yield ')?'
        else:
            ignorable = False
            if token == '-lrb-':
                in_paren = True
                ignorable = True
            elif token == '-rrb-':
                in_paren = False
                ignorable = True
            elif token in ('-lrb-', '-rrb-', ',', '.'):
                ignorable = True
            elif token in IGNORABLE_TOKENS.get(entity_type, ()):
                ignorable = True
            elif entity_type == 'sportradar' and NUMBER_PATTERN.match(token):
                ignorable = True
            if in_paren or ignorable:
                yield '"' + token + '"?'
            else:
                yield '"' + token + '"'
    yield ')'


def main():
    dbdata = urllib.parse.urlparse(os.environ['DATABASE_URL'], allow_fragments=False)
    if dbdata.scheme != 'mysql':
        print("Invalid database url", file=sys.stderr)
        sys.exit(1)
    
    db = dbdata.path
    if db.startswith('/'):
        db = db[1:]
    conn = MySQLdb.connect(user=dbdata.username,
                           passwd=dbdata.password,
                           host=dbdata.hostname,
                           port=(dbdata.port or 3306),
                           db=db,
                           use_unicode=True,
                           charset='utf8mb4',
                           ssl=dict(ca=os.path.join(os.path.dirname(__file__), '../data/thingpedia-db-ca-bundle.pem')))
    cursor = conn.cursor(cursorclass=MySQLdb.cursors.DictCursor)
    cursor.execute("select distinct entity_id, entity_name from entity_lexicon where language = %s", [sys.argv[1]])
    
    with socket.create_connection(('127.0.0.1', 8888)) as tokenizer:
        tokenizerfile = tokenizer.makefile()
        for row in cursor.fetchall():
            msg = json.dumps(dict(languageTag=sys.argv[1], utterance=row['entity_name']))
            tokenizer.send((msg + '\n').encode('utf-8'))
            result = json.loads(tokenizerfile.readline())
            
            # conflate all sportradar in one (because often the same university has a football and a basketball team
            # which show up as different entities in our database)
            entity_type = row['entity_id']
            if entity_type.startswith('sportradar:'):
                entity_type = 'sportradar'
            
            print(' '.join(process_tokens(entity_type, result['rawTokens'])), 'GENERIC_ENTITY_' + entity_type,
                  ','.join(OVERRIDABLE_TYPES.get(entity_type, [])), PRIORITY.get(entity_type, 0), sep='\t')
            
if __name__ == '__main__':
    main()
