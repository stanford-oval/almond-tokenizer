#!/bin/sh

find src/ -name \*.java | awk -e '{ print "core "$1; }' | sed 's|/|.|g
s|src\.||
s|\.java||' > module-classes.txt
