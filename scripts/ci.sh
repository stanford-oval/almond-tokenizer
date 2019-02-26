#!/bin/bash

## Integration tests

set -e
set -x
set -o pipefail

srcdir=`dirname $0`/..
srcdir=`realpath $srcdir`
cd $srcdir

on_error() {
    test -n "$serverpid" && kill $serverpid || true
    serverpid=
    wait
}
trap on_error ERR INT TERM

./run.sh &
serverpid=$!

if test "$1" = "--interactive" ; then
    sleep 84600
else
    # sleep until the process is settled
    sleep 45
    
    ./scripts/test-tokenizer.py < $srcdir/data/test-tokenizer-en.yml
    ./scripts/test-tokenizer.py < $srcdir/data/test-tokenizer-zh.yml
fi

kill $serverpid
serverpid=