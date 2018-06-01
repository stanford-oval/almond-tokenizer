#!/usr/bin/python3

import json
import sys
import socket

def remove_quotes_program(program, entities):
    if isinstance(program, str):
        program = program.split(' ')
    for token in program:
        if token.startswith('QUOTED_STRING_'):
            value = entities[token[len('QUOTED_'):]]
            if value in ("'' ''", "`` ''", "'' ``"):
                yield '""'
                continue
            yield '"'
            if value.startswith('`` ') or value.startswith("'' "):
                value = value[3:]
            if value.endswith(" ''"):
                value = value[:-3]
            yield value
            yield '"'
        elif token.startswith('USERNAME_'):
            value = entities[token[len('USER'):]]
            yield '"'
            yield value
            yield '"'
            yield '^^tt:username'
        elif token.startswith('HASHTAG_'):
            value = entities[token[len('HASH'):]]
            yield '"'
            yield value
            yield '"'
            yield '^^tt:hashtag'
        elif token.startswith('GENERIC_ENTITY_'):
            value = entities[token[len('GENERIC_'):]]
            yield '"'
            yield value
            yield '"'
            yield '^^' + token[len('GENERIC_ENTITY_'):-2]
        else:
            yield token

def count_tokens(tokens):
    count = 0
    for token in tokens:
        if token[0].isupper():
            count += int(token.rsplit('*', maxsplit=1)[1])
        else:
            count += 1
    return count

def remove_star(tokens):
    for token in tokens:
        if token[0].isupper():
            yield token.rsplit('*', maxsplit=1)[0]
        else:
            yield token

with socket.create_connection(('127.0.0.1', 8888)) as conn:
    i = 0
    connfile = conn.makefile()
    for line in sys.stdin:
        _id, raw, preprocessed, program = line.strip().split('\t')
        
        msg = json.dumps(dict(languageTag='en', utterance=raw, req=i))
        conn.send((msg + '\n').encode('utf-8'))
        result = json.loads(connfile.readline())
        
        if count_tokens(result['tokensNoQuotes']) != count_tokens(result['tokens']):
            print('Failed on', _id, 'inconsistent number of tokens', file=sys.stderr)
        elif preprocessed != ' '.join(remove_star(result['tokens'])):
            print('Failed on', _id, 'wanted [[', preprocessed, ']] got [[', ' '.join(result['tokens']), ']]', file=sys.stderr)
        else:
            print(_id, ' '.join(result['tokensNoQuotes']), ' '.join(result['tokens']), program, ' '.join(remove_quotes_program(program, result['values'])), file=sys.stdout, sep='\t')
        i += 1