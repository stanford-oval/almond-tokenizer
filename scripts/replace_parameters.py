#!/usr/bin/python3

import json
import sys
import socket
import os
import re

import numpy as np
from bottleneck.nonreduce import replace

class TokenizerService:
    def __init__(self):
        self._conn = socket.create_connection(('127.0.0.1', 8888))
        self._connfile = self._conn
        self._i = 0
        
    def tokenize(self, utterance):
        msg = json.dumps(dict(languageTag='en', utterance=line.strip(), req=self._i))
        self._i += 1
        conn.send((msg + '\n').encode('utf-8'))
        return json.loads(connfile.readline())

#_tokenizer = TokenizerService()
_loaded_parameters = {
}

def get_param_values(pid):
    fn, pname, ptype, pop = pid.split(':')
    
    if fn + '.' + pname in _loaded_parameters:
        if pop == 'contains':
            # TODO sample words
            return []
        else:
            return _loaded_parameters[fn + '.' + pname]
    elif os.path.exists(fn + '.' + pname + '.txt'):
        value_list = []
        _loaded_parameters[fn + '.' + pname] = value_list
        with open(fn + '.' + pname + '.txt', 'r') as fp:
            for line in fp:
                line = line.strip()
                if any(re.match('[0-9.]+', x) for x in line.split(' ')):
                    continue
                value_list.append(line)
        return value_list
    else:
        return []

def is_replace_token(tok):
    return tok.startswith('QUOTED_STRING_') or tok.startswith('HASHTAG_') or tok.startswith('USERNAME_')

def replace_tokens_in_sentence(sentence, parameters, replacements):
    for token in sentence:
        if token in replacements:
            yield replacements[token]
        elif is_replace_token(token):
            replace = get_param_values(parameters[token])
            if not replace:
                raise ValueError('no values for parameter ' + parameters[token])
            replacements[token] = np.random.choice(replace)
            yield replacements[token]
        else:
            yield token

def replace_tokens_in_program(program, replacements):
    for token in program:
        if is_replace_token(token):
            yield replacements[token]
        else:
            yield token

def main():
    np.random.seed(1234)
    
    for line in sys.stdin:
        _id, sentence, program = line.strip().split('\t')
        
        sentence = sentence.split(' ')
        program = program.split(' ')
        
        parameters = dict()
        cur_fn = None
        cur_param = None
        cur_op = None
        for token in program:
            if token.startswith('@'):
                cur_fn = token[1:]
            elif token.startswith('param:'):
                cur_param = token[len('param:'):]
            elif token in ('==', '=', '=~', '~=', 'in_array', 'contains', 'starts_with', 'ends_with', '>=', '<='):
                cur_op = token
            elif is_replace_token(token):
                parameters[token] = cur_fn + ':' + cur_param + ':' + cur_op
            
        replacements = dict()
        try:
            new_sentence = ' '.join(replace_tokens_in_sentence(sentence, parameters, replacements))
            new_program = ' '.join(replace_tokens_in_program(program, replacements))
            print('R' + _id, new_sentence, new_program, sep='\t')
        except Exception as e:
            print(e, file=sys.stderr)
        
if __name__ == '__main__':
    main()