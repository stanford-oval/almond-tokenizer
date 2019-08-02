#!/usr/bin/python3

import json
import yaml
import sys
import socket

with socket.create_connection(('127.0.0.1', 8888)) as conn:
    i = 0
    connfile = conn.makefile()
    loaded = yaml.load(sys.stdin)
    for line in loaded:
        if 'expect' in line:
            msg = json.dumps(dict(languageTag=line['locale'], utterance=line['input'], expect=line['expect'], req=i))
        else:
            msg = json.dumps(dict(languageTag=line['locale'], utterance=line['input'], req=i))
        #print(msg, file=sys.stderr)
        conn.send((msg + '\n').encode('utf-8'))
        result = json.loads(connfile.readline())
        if 'error' in result:
            print('Returned error', file=sys.stderr)
            print(result, file=sys.stderr)
            sys.exit(1)
        if ' '.join(result['tokens']) != line['tokens']:
            print('Failed, wrong tokens', file=sys.stderr)
            print(result, file=sys.stderr)
            sys.exit(1)
        if ' '.join(result['rawTokens']) != line['rawTokens']:
            print('Failed, wrong raw tokens', file=sys.stderr)
            print(result, file=sys.stderr)
            sys.exit(1)
        if line['entities'] != result['values']:
            print('Failed, wrong entities', file=sys.stderr)
            print(result, file=sys.stderr)
            sys.exit(1)
        i += 1
